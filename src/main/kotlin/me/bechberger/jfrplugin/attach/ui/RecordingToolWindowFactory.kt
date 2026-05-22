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
import me.bechberger.jfrplugin.config.profilerConfig
import me.bechberger.jfrplugin.viewer.JeffreyLauncher
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

class RecordingToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = RecordingPanel(project)
        @Suppress("DEPRECATION")
        val content = ContentFactory.SERVICE.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

private class RecordingPanel(private val project: Project) : JPanel(BorderLayout()) {
    private val service = AttachService.getInstance(project)
    private val tableModel = JvmTableModel()
    private val table = JTable(tableModel)

    private val ticker = Timer(1000) { tableModel.tick() }

    init {
        table.rowHeight = 28
        table.preferredScrollableViewportSize = Dimension(600, 250)

        // Actions column: use a custom renderer+editor with inline buttons
        val actionsCol = table.columnModel.getColumn(COL_ACTIONS)
        actionsCol.preferredWidth = 260
        actionsCol.cellRenderer = ActionsCellRenderer()
        actionsCol.cellEditor = ActionsCellEditor(project, service, tableModel, ticker) { table }

        // Clicking anywhere in the row triggers editing (so buttons are live)
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val row = table.rowAtPoint(e.point)
                val col = table.columnAtPoint(e.point)
                if (row >= 0 && col == COL_ACTIONS) {
                    table.editCellAt(row, COL_ACTIONS)
                }
            }
        })

        val toolbar = JPanel().apply {
            add(JButton("Refresh").also { btn ->
                btn.addActionListener { refresh() }
            })
        }
        add(toolbar, BorderLayout.NORTH)
        add(JScrollPane(table), BorderLayout.CENTER)

        ticker.start()
        refresh()
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

    private val columns = arrayOf("PID", "Name", "State", "Profile with")

    fun update(newJvms: List<JvmInfo>, service: AttachService) {
        jvms = newJvms
        states = newJvms.associate { it.pid to service.state(it.pid) }.toMutableMap()
        fireTableDataChanged()
    }

    fun setState(pid: String, state: RecordingState) {
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
    override fun isCellEditable(row: Int, col: Int) = col == RecordingPanel.COL_ACTIONS

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

// ── Actions cell ─────────────────────────────────────────────────────────────

private fun actionsPanel(
    state: RecordingState,
    jfrExists: Boolean,
    onStart: (Engine) -> Unit,
    onStop: () -> Unit,
    onOpenJeffrey: () -> Unit,
): JPanel {
    return JPanel().apply {
        isOpaque = true
        if (state is RecordingState.Idle) {
            add(JButton("JFR").also { it.addActionListener { onStart(Engine.JFR) } })
            add(JButton("async-profiler").also { it.addActionListener { onStart(Engine.ASYNC_PROFILER) } })
            if (jfrExists) {
                add(JButton("Open with Jeffrey").also { it.addActionListener { onOpenJeffrey() } })
            }
        } else {
            add(JButton("Stop & Open").also { it.addActionListener { onStop() } })
        }
    }
}

private class ActionsCellRenderer : TableCellRenderer {
    override fun getTableCellRendererComponent(
        table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
    ): Component {
        val state = value as? RecordingState ?: RecordingState.Idle
        return actionsPanel(state, false, {}, {}, {})
    }
}

private class ActionsCellEditor(
    private val project: Project,
    private val service: AttachService,
    private val model: JvmTableModel,
    private val ticker: Timer,
    private val tableRef: () -> JTable,
) : AbstractCellEditor(), TableCellEditor {

    private var currentPanel: JPanel? = null
    private var currentPid: String = ""

    override fun getCellEditorValue(): Any = model.states[currentPid] ?: RecordingState.Idle

    override fun getTableCellEditorComponent(
        table: JTable, value: Any?, isSelected: Boolean, row: Int, column: Int
    ): Component {
        currentPid = model.jvms.getOrNull(row)?.pid ?: return JPanel()
        val state = value as? RecordingState ?: RecordingState.Idle
        val jfrExists = Files.exists(project.profilerConfig.getJFRFile(project))
        val panel = actionsPanel(
            state,
            jfrExists,
            onStart = { engine -> doStart(currentPid, engine, row) },
            onStop = { doStop(currentPid, row) },
            onOpenJeffrey = { doOpenJeffrey() }
        )
        currentPanel = panel
        return panel
    }

    private fun doOpenJeffrey() {
        stopCellEditing()
        val jfrFile = project.profilerConfig.getJFRFile(project)
        ApplicationManager.getApplication().executeOnPooledThread {
            val url = JeffreyLauncher.startAndGetUrl(jfrFile) ?: run {
                SwingUtilities.invokeLater {
                    JOptionPane.showMessageDialog(tableRef(),
                        "Jeffrey is not available. Run './gradlew downloadJeffrey' to install it.",
                        "Jeffrey Not Found", JOptionPane.WARNING_MESSAGE)
                }
                return@executeOnPooledThread
            }
            SwingUtilities.invokeLater { JFRProgramRunner.loadFile(project, url) }
        }
    }

    private fun doStart(pid: String, engine: Engine, @Suppress("UNUSED_PARAMETER") row: Int) {
        stopCellEditing()
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

    private fun doStop(pid: String, @Suppress("UNUSED_PARAMETER") row: Int) {
        stopCellEditing()
        SwingUtilities.invokeLater { model.setState(pid, RecordingState.Idle) }
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val outputFile = service.stop(pid)
                LocalFileSystem.getInstance().refreshNioFiles(listOf(outputFile), false, false, null)
                SwingUtilities.invokeLater {
                    model.setState(pid, RecordingState.Idle)
                    JFRProgramRunner.loadFile(project)
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    JOptionPane.showMessageDialog(tableRef(), "Failed to stop recording:\n${e.message}", "Error", JOptionPane.ERROR_MESSAGE)
                }
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun formatElapsed(start: Instant): String {
    val secs = Duration.between(start, Instant.now()).seconds
    return "%d:%02d".format(secs / 60, secs % 60)
}
