package me.bechberger.jfrplugin.attach.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import me.bechberger.jfrplugin.attach.AttachService
import me.bechberger.jfrplugin.attach.Engine
import me.bechberger.jfrplugin.attach.JvmInfo
import me.bechberger.jfrplugin.attach.RecordingState
import me.bechberger.jfrplugin.runner.jfr.JFRProgramRunner
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

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

    private val refreshButton = JButton("Refresh")
    private val engineCombo = JComboBox(arrayOf(Engine.JFR, Engine.ASYNC_PROFILER)).apply {
        renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
            ): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                text = if (value == Engine.JFR) "JFR" else "async-profiler"
                return this
            }
        }
    }
    private val startButton = JButton("Start").apply { isEnabled = false }
    private val stopButton = JButton("Stop & Open").apply { isEnabled = false }

    init {
        table.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        table.selectionModel.addListSelectionListener { updateButtons() }
        table.rowHeight = 22
        table.preferredScrollableViewportSize = Dimension(400, 200)

        val toolbar = JPanel().apply {
            add(refreshButton)
            add(JLabel("Engine:"))
            add(engineCombo)
            add(startButton)
            add(stopButton)
        }
        add(toolbar, BorderLayout.NORTH)
        add(JScrollPane(table), BorderLayout.CENTER)

        refreshButton.addActionListener { refresh() }
        startButton.addActionListener { startRecording() }
        stopButton.addActionListener { stopRecording() }

        refresh()
    }

    private fun refresh() {
        val jvms = service.listJvms()
        tableModel.update(jvms, service)
        updateButtons()
    }

    private fun updateButtons() {
        val row = table.selectedRow
        if (row < 0 || row >= tableModel.jvms.size) {
            startButton.isEnabled = false
            stopButton.isEnabled = false
            return
        }
        val pid = tableModel.jvms[row].pid
        val state = service.state(pid)
        startButton.isEnabled = state is RecordingState.Idle
        stopButton.isEnabled = state is RecordingState.Recording
    }

    private fun startRecording() {
        val row = table.selectedRow.takeIf { it >= 0 } ?: return
        val pid = tableModel.jvms[row].pid
        val engine = engineCombo.selectedItem as Engine
        SwingUtilities.invokeLater {
            try {
                service.start(pid, engine)
                tableModel.fireTableRowsUpdated(row, row)
                updateButtons()
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(this, "Failed to start recording:\n${e.message}", "Error", JOptionPane.ERROR_MESSAGE)
            }
        }
    }

    private fun stopRecording() {
        val row = table.selectedRow.takeIf { it >= 0 } ?: return
        val pid = tableModel.jvms[row].pid
        SwingUtilities.invokeLater {
            try {
                service.stop(pid)
                tableModel.fireTableRowsUpdated(row, row)
                updateButtons()
                JFRProgramRunner.loadFile(project)
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(this, "Failed to stop recording:\n${e.message}", "Error", JOptionPane.ERROR_MESSAGE)
            }
        }
    }
}

private class JvmTableModel : AbstractTableModel() {
    var jvms: List<JvmInfo> = emptyList()
        private set
    private var states: Map<String, RecordingState> = emptyMap()

    private val columns = arrayOf("PID", "Name", "State")

    fun update(newJvms: List<JvmInfo>, service: AttachService) {
        jvms = newJvms
        states = newJvms.associate { it.pid to service.state(it.pid) }
        fireTableDataChanged()
    }

    override fun getRowCount() = jvms.size
    override fun getColumnCount() = columns.size
    override fun getColumnName(column: Int) = columns[column]

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val jvm = jvms[rowIndex]
        return when (columnIndex) {
            0 -> jvm.pid
            1 -> jvm.displayName
            2 -> when (val s = states[jvm.pid] ?: RecordingState.Idle) {
                is RecordingState.Idle -> "Idle"
                is RecordingState.Recording -> "Recording (${if (s.engine == Engine.JFR) "JFR" else "ap"})"
            }
            else -> ""
        }
    }
}
