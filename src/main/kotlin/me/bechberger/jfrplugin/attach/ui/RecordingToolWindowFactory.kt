package me.bechberger.jfrplugin.attach.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.SearchTextField
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
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
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
    private val searchField = SearchTextField(false)

    private val ticker = Timer(1000) { tableModel.tick() }
    private val autoRefresh = Timer(5000) { refresh() }

    init {
        val buttonHeight = JButton("X").apply { margin = java.awt.Insets(1, 6, 1, 6) }.preferredSize.height
        table.rowHeight = buttonHeight + 6
        table.preferredScrollableViewportSize = Dimension(600, 220)
        table.setShowGrid(false)
        table.intercellSpacing = Dimension(0, 0)
        table.tableHeader.reorderingAllowed = false

        // Column widths
        with(table.columnModel) {
            getColumn(COL_PID).apply    { minWidth = 48;  maxWidth = 64;  preferredWidth = 56  }
            getColumn(COL_NAME).apply   { preferredWidth = 220 }
            getColumn(COL_STATE).apply  { minWidth = 60;  maxWidth = 110; preferredWidth = 90  }
            getColumn(COL_ACTIONS).apply { preferredWidth = 280; cellRenderer = renderer }
        }

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

        searchField.textEditor.emptyText.text = "Filter by name or PID…"
        searchField.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = applyFilter()
            override fun removeUpdate(e: DocumentEvent) = applyFilter()
            override fun changedUpdate(e: DocumentEvent) = applyFilter()
        })

        val refreshBtn = JButton("↺ Refresh").apply {
            margin = java.awt.Insets(1, 8, 1, 8)
            isFocusPainted = false
            addActionListener { refresh() }
        }

        val toolbar = JPanel(BorderLayout(4, 0)).apply {
            add(searchField, BorderLayout.CENTER)
            add(refreshBtn, BorderLayout.EAST)
            border = BorderFactory.createEmptyBorder(3, 4, 3, 4)
        }

        add(toolbar, BorderLayout.NORTH)
        add(JScrollPane(table).apply {
            border = BorderFactory.createEmptyBorder()
        }, BorderLayout.CENTER)

        ticker.start()
        autoRefresh.start()
        refresh()
    }

    private fun applyFilter() {
        val query = searchField.text.trim().lowercase()
        tableModel.setFilter(query)
    }

    private fun handleMouse(e: MouseEvent) {
        val row = table.rowAtPoint(e.point)
        val col = table.columnAtPoint(e.point)
        if (row < 0 || col != COL_ACTIONS) return

        val cellRect = table.getCellRect(row, col, false)
        val panel = renderer.getPanelForRow(row) ?: return
        val localPoint = Point(e.x - cellRect.x, e.y - cellRect.y)
        panel.size = cellRect.size
        panel.doLayout()

        val hit = SwingUtilities.getDeepestComponentAt(panel, localPoint.x, localPoint.y)
        if (hit is JButton) hit.doClick()
    }

    fun refresh() {
        ApplicationManager.getApplication().executeOnPooledThread {
            val jvms = service.listJvms()
            SwingUtilities.invokeLater {
                tableModel.update(jvms, service)
            }
        }
    }

    companion object {
        const val COL_PID     = 0
        const val COL_NAME    = 1
        const val COL_STATE   = 2
        const val COL_ACTIONS = 3
    }
}

// ── Table model ──────────────────────────────────────────────────────────────

private class JvmTableModel : AbstractTableModel() {
    private var allJvms: List<JvmInfo> = emptyList()
    private var filter: String = ""

    var jvms: List<JvmInfo> = emptyList()
        private set
    var states: MutableMap<String, RecordingState> = mutableMapOf()
        private set
    val lastOutputFile: MutableMap<String, java.nio.file.Path> = mutableMapOf()

    private val columns = arrayOf("PID", "Name", "State", "Actions")

    fun setFilter(query: String) {
        filter = query
        rebuildView()
    }

    fun update(newJvms: List<JvmInfo>, service: AttachService) {
        // Add states only for PIDs we haven't seen before; preserve existing (e.g. Recording)
        for (jvm in newJvms) {
            if (jvm.pid !in states) states[jvm.pid] = service.state(jvm.pid)
        }
        allJvms = newJvms
        rebuildView()
    }

    private fun rebuildView() {
        val newView = if (filter.isEmpty()) allJvms
        else allJvms.filter { jvm ->
            jvm.pid.contains(filter, ignoreCase = true) ||
            jvm.displayName.contains(filter, ignoreCase = true)
        }
        val structuralChange = newView.map { it.pid } != jvms.map { it.pid }
        jvms = newView
        if (structuralChange) fireTableDataChanged()
        else if (jvms.isNotEmpty()) fireTableRowsUpdated(0, jvms.size - 1)
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
                is RecordingState.Idle      -> "Idle"
                is RecordingState.Recording -> "● ${if (s.engine == Engine.JFR) "JFR" else "AP"} ${formatElapsed(s.startTime)}"
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

    private val panels = mutableMapOf<Int, JPanel>()

    fun getPanelForRow(row: Int): JPanel? = panels[row]

    override fun getTableCellRendererComponent(
        table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
    ): Component {
        val pid = model.jvms.getOrNull(row)?.pid ?: return JPanel()
        val state = value as? RecordingState ?: RecordingState.Idle
        val existingFile = when (state) {
            is RecordingState.Recording -> state.outputFile
            is RecordingState.Idle      -> model.lastOutputFile[pid]
        }
        val jfrExists = existingFile != null && Files.exists(existingFile)

        val panel = buildPanel(pid, state, jfrExists)
        panels[row] = panel
        panel.background = if (isSelected) table.selectionBackground else table.background
        return panel
    }

    private fun buildPanel(pid: String, state: RecordingState, jfrExists: Boolean): JPanel {
        val panel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 3, 2)).apply { isOpaque = true }
        val jeffreyAvailable = JeffreyLauncher.isJdkAvailable()

        if (state is RecordingState.Idle) {
            panel.add(btn("JFR") { doStart(pid, Engine.JFR) })
            panel.add(btn("AP")  { doStart(pid, Engine.ASYNC_PROFILER) })
            if (jfrExists) {
                panel.add(btn("Open") { doOpen(pid) })
                if (jeffreyAvailable) panel.add(btn("Jeffrey") { doOpenJeffrey(pid) })
            }
        } else {
            panel.add(btn("Stop") { doStop(pid) })
            if (jfrExists) {
                panel.add(btn("Open") { doOpen(pid) })
                if (jeffreyAvailable) panel.add(btn("Jeffrey") { doOpenJeffrey(pid) })
            }
        }
        return panel
    }

    private fun btn(label: String, enabled: Boolean = true, action: (() -> Unit)? = null): JButton =
        JButton(label).apply {
            isEnabled = enabled
            margin = java.awt.Insets(1, 6, 1, 6)
            isFocusPainted = false
            if (action != null) addActionListener { action() }
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
            ?: model.lastOutputFile[pid] ?: return
        ApplicationManager.getApplication().invokeLater {
            JFRProgramRunner.loadFile(project, outputFile)
        }
    }

    private fun doOpenJeffrey(pid: String) {
        val jfrFile = (model.states[pid] as? RecordingState.Recording)?.outputFile
            ?: model.lastOutputFile[pid] ?: return
        ApplicationManager.getApplication().invokeLater {
            JFRProgramRunner.loadFile(project, jfrFile)
            val vf = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                .refreshAndFindFileByNioFile(jfrFile) ?: return@invokeLater
            com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
                .setSelectedEditor(vf, "Jeffrey Profile")
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun formatElapsed(start: Instant): String {
    val secs = Duration.between(start, Instant.now()).seconds
    return "%d:%02d".format(secs / 60, secs % 60)
}
