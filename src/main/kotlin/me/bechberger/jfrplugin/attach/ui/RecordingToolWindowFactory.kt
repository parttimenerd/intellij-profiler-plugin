package me.bechberger.jfrplugin.attach.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import me.bechberger.jfrplugin.attach.AttachService
import me.bechberger.jfrplugin.attach.Engine
import me.bechberger.jfrplugin.attach.JvmInfo
import me.bechberger.jfrplugin.attach.RecordingState
import me.bechberger.jfrplugin.runner.jfr.JFRProgramRunner
import me.bechberger.jfrplugin.viewer.JeffreyLauncher
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableCellRenderer

class RecordingToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = RecordingPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

private class RecordingPanel(private val project: Project) : JPanel(BorderLayout()) {
    private val service = AttachService.getInstance(project)
    private val tableModel = JvmTableModel()
    private val table = JTable(tableModel)
    private val renderer = ActionsCellRenderer(project, service, tableModel) { table }

    private val ticker = Timer(1000) { tableModel.tick() }

    init {
        table.rowHeight = 28
        table.preferredScrollableViewportSize = Dimension(700, 250)

        val actionsCol = table.columnModel.getColumn(COL_ACTIONS)
        actionsCol.preferredWidth = 360
        actionsCol.cellRenderer = renderer

        // Never use a cell editor — all interaction goes through the mouse listener
        table.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = handleMouse(e)
        })
        table.addMouseMotionListener(object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val col = table.columnAtPoint(e.point)
                table.cursor = if (col == COL_ACTIONS)
                    java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
                else
                    java.awt.Cursor.getDefaultCursor()
            }
        })

        val toolbar = JPanel().apply {
            add(JButton("Refresh").also { btn -> btn.addActionListener { refresh() } })
        }
        add(toolbar, BorderLayout.NORTH)
        add(JScrollPane(table), BorderLayout.CENTER)

        ticker.start()
        refresh()
    }

    private fun handleMouse(e: MouseEvent) {
        val row = table.rowAtPoint(e.point)
        val col = table.columnAtPoint(e.point)
        if (row < 0 || col != COL_ACTIONS) return

        // Translate the click into the renderer panel's coordinate space
        val cellRect = table.getCellRect(row, col, false)
        val panel = renderer.getPanelForRow(row) ?: return
        val localPoint = Point(e.x - cellRect.x, e.y - cellRect.y)
        panel.size = cellRect.size
        panel.doLayout()

        // Find which button was hit
        val hit = SwingUtilities.getDeepestComponentAt(panel, localPoint.x, localPoint.y)
        if (hit is JButton) hit.doClick()
    }

    fun refresh() {
        val jvms = service.listJvms()
        tableModel.update(jvms, service)
    }

    companion object {
        const val COL_ACTIONS = 3
    }
}

// ── Table model ──────────────────────────────────────────────────────────────

private class JvmTableModel : AbstractTableModel() {
    var jvms: List<JvmInfo> = emptyList()
        private set
    var states: MutableMap<String, RecordingState> = mutableMapOf()
        private set
    val lastOutputFile: MutableMap<String, java.nio.file.Path> = mutableMapOf()

    private val columns = arrayOf("PID", "Name", "State", "Actions")

    fun update(newJvms: List<JvmInfo>, service: AttachService) {
        jvms = newJvms
        states = newJvms.associate { it.pid to service.state(it.pid) }.toMutableMap()
        fireTableDataChanged()
    }

    fun setState(pid: String, state: RecordingState) {
        val prev = states[pid]
        if (prev is RecordingState.Recording && state is RecordingState.Idle) {
            lastOutputFile[pid] = prev.outputFile
        }
        states[pid] = state
        val row = jvms.indexOfFirst { it.pid == pid }
        if (row >= 0) fireTableRowsUpdated(row, row)
    }

    fun tick() {
        jvms.forEachIndexed { i, jvm ->
            if (states[jvm.pid] is RecordingState.Recording) fireTableRowsUpdated(i, i)
        }
    }

    override fun getRowCount() = jvms.size
    override fun getColumnCount() = columns.size
    override fun getColumnName(col: Int) = columns[col]
    override fun isCellEditable(row: Int, col: Int) = false

    override fun getValueAt(row: Int, col: Int): Any {
        val jvm = jvms[row]
        return when (col) {
            0 -> jvm.pid
            1 -> jvm.displayName
            2 -> when (val s = states[jvm.pid] ?: RecordingState.Idle) {
                is RecordingState.Idle -> "Idle"
                is RecordingState.Recording -> "● ${if (s.engine == Engine.JFR) "JFR" else "ap"} ${formatElapsed(s.startTime)}"
            }
            3 -> states[jvm.pid] ?: RecordingState.Idle
            else -> ""
        }
    }
}

// ── Actions cell renderer (no editor) ────────────────────────────────────────

private class ActionsCellRenderer(
    private val project: Project,
    private val service: AttachService,
    private val model: JvmTableModel,
    private val tableRef: () -> JTable,
) : TableCellRenderer {

    // One persistent panel per row index — rebuilt when row count changes
    private val panels = mutableMapOf<Int, JPanel>()

    fun getPanelForRow(row: Int): JPanel? = panels[row]

    override fun getTableCellRendererComponent(
        table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
    ): Component {
        val pid = model.jvms.getOrNull(row)?.pid ?: return JPanel()
        val state = value as? RecordingState ?: RecordingState.Idle
        val existingFile = when (state) {
            is RecordingState.Recording -> state.outputFile
            is RecordingState.Idle -> model.lastOutputFile[pid]
        }
        val jfrExists = existingFile != null && Files.exists(existingFile)

        val panel = buildPanel(pid, state, jfrExists)
        panels[row] = panel
        if (isSelected) panel.background = table.selectionBackground
        else panel.background = table.background
        return panel
    }

    private fun buildPanel(pid: String, state: RecordingState, jfrExists: Boolean): JPanel {
        val panel = JPanel().apply { isOpaque = true }
        if (state is RecordingState.Idle) {
            panel.add(btn("JFR") { doStart(pid, Engine.JFR) })
            panel.add(btn("async-profiler") { doStart(pid, Engine.ASYNC_PROFILER) })
            if (jfrExists) {
                panel.add(btn("Open") { doOpen(pid) })
                panel.add(btn("Open with Jeffrey") { doOpenJeffrey(pid) })
            }
        } else {
            panel.add(btn("JFR", enabled = false))
            panel.add(btn("async-profiler", enabled = false))
            panel.add(btn("Stop") { doStop(pid) })
            panel.add(btn("Open") { doOpen(pid) })
            panel.add(btn("Open with Jeffrey") { doOpenJeffrey(pid) })
        }
        return panel
    }

    private fun btn(label: String, enabled: Boolean = true, action: (() -> Unit)? = null): JButton {
        return JButton(label).apply {
            isEnabled = enabled
            val insets = java.awt.Insets(1, 6, 1, 6)
            margin = insets
            isFocusPainted = false
            if (action != null) addActionListener { action() }
        }
    }

    private fun doStart(pid: String, engine: Engine) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                service.start(pid, engine)
                SwingUtilities.invokeLater { model.setState(pid, service.state(pid)) }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    JOptionPane.showMessageDialog(tableRef(), "Failed to start recording:\n${e.message}", "Error", JOptionPane.ERROR_MESSAGE)
                }
            }
        }
    }

    private fun doStop(pid: String) {
        val outputFile = (model.states[pid] as? RecordingState.Recording)?.outputFile
        SwingUtilities.invokeLater { model.setState(pid, RecordingState.Idle) }
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val stopped = service.stop(pid)
                val file = outputFile ?: stopped
                LocalFileSystem.getInstance().refreshNioFiles(listOf(file), false, false, null)
                SwingUtilities.invokeLater { model.setState(pid, RecordingState.Idle) }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    JOptionPane.showMessageDialog(tableRef(), "Failed to stop recording:\n${e.message}", "Error", JOptionPane.ERROR_MESSAGE)
                }
            }
        }
    }

    private fun doOpen(pid: String) {
        val outputFile = (model.states[pid] as? RecordingState.Recording)?.outputFile
            ?: model.lastOutputFile[pid]
            ?: return
        ApplicationManager.getApplication().invokeLater {
            JFRProgramRunner.loadFile(project, outputFile)
        }
    }

    private fun doOpenJeffrey(pid: String) {
        val jfrFile = (model.states[pid] as? RecordingState.Recording)?.outputFile
            ?: model.lastOutputFile[pid]
            ?: return
        ApplicationManager.getApplication().executeOnPooledThread {
            val url = JeffreyLauncher.startAndGetUrl(jfrFile) ?: run {
                SwingUtilities.invokeLater {
                    JOptionPane.showMessageDialog(tableRef(),
                        "Jeffrey is not available. Run './gradlew downloadJeffrey' to install it.",
                        "Jeffrey Not Found", JOptionPane.WARNING_MESSAGE)
                }
                return@executeOnPooledThread
            }
            SwingUtilities.invokeLater { JFRProgramRunner.loadFile(project, jfrFile, url) }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun formatElapsed(start: Instant): String {
    val secs = Duration.between(start, Instant.now()).seconds
    return "%d:%02d".format(secs / 60, secs % 60)
}
