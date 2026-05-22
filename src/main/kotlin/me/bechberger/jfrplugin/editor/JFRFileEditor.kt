package me.bechberger.jfrplugin.editor

import com.intellij.diff.util.FileEditorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import me.bechberger.jfrplugin.config.ViewerType
import me.bechberger.jfrplugin.config.profilerConfig
import me.bechberger.jfrplugin.viewer.JeffreyLauncher
import java.awt.BorderLayout
import java.awt.Component
import java.util.logging.Logger
import javax.swing.DefaultListCellRenderer
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel

private val VIEWER_LABELS = mapOf(
    ViewerType.FIREFOX_PROFILER to "Firefox Profiler",
    ViewerType.JEFFREY to "Jeffrey"
)

class JFRFileEditor(private val project: Project, private val virtualFile: VirtualFile) : FileEditorBase() {
    private val viewComponent: JComponent
    val webViewWindow: WebViewWindow
    private val messageBusConnection = project.messageBus.connect()
    private val fileChangedListener = FileChangedListener(true)

    init {
        if (!virtualFile.exists() || !(virtualFile.path.endsWith(".jfr") || virtualFile.path.endsWith(".json.gz"))) {
            throw IllegalArgumentException("File must exist and have .jfr or .json.gz extension")
        }

        messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, fileChangedListener)

        val defaultViewer = project.profilerConfig.defaultViewer
        val initialUrl = initialJeffreyUrl(defaultViewer)

        webViewWindow = WebViewWindow(
            project,
            project.profilerConfig.conversionConfig.toConfig(),
            virtualFile.toNioPath(),
            initialUrl
        )

        viewComponent = buildViewComponent(defaultViewer)
    }

    private fun initialJeffreyUrl(viewer: ViewerType): String? {
        if (viewer == ViewerType.JEFFREY && virtualFile.path.endsWith(".jfr")) {
            return JeffreyLauncher.startAndGetUrl(virtualFile.toNioPath())
        }
        return null
    }

    private fun buildViewComponent(defaultViewer: ViewerType): JComponent {
        val panel = JPanel(BorderLayout())

        // Toolbar with viewer selector (only for .jfr files — .json.gz is Firefox Profiler only)
        if (virtualFile.path.endsWith(".jfr")) {
            val viewerCombo = JComboBox(arrayOf(ViewerType.FIREFOX_PROFILER, ViewerType.JEFFREY)).also { combo ->
                combo.selectedItem = defaultViewer
                combo.renderer = object : DefaultListCellRenderer() {
                    override fun getListCellRendererComponent(
                        list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
                    ): Component {
                        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                        text = VIEWER_LABELS[value as? ViewerType] ?: value?.toString() ?: ""
                        return this
                    }
                }
                combo.addActionListener {
                    switchViewer(combo.selectedItem as ViewerType)
                }
            }

            val toolbar = JPanel().apply {
                add(JLabel("Viewer:"))
                add(viewerCombo)
            }
            panel.add(toolbar, BorderLayout.NORTH)
        }

        panel.add(webViewWindow.component, BorderLayout.CENTER)
        return panel
    }

    private fun switchViewer(viewer: ViewerType) {
        when (viewer) {
            ViewerType.FIREFOX_PROFILER -> {
                JeffreyLauncher.stop()
                webViewWindow.reloadWithFirefoxProfiler()
            }
            ViewerType.JEFFREY -> {
                val url = JeffreyLauncher.startAndGetUrl(virtualFile.toNioPath()) ?: run {
                    logger.warning("Jeffrey not available — microscope.jar not bundled")
                    return
                }
                webViewWindow.loadUrl(url)
            }
        }
    }

    override fun getName(): String = NAME

    override fun getFile(): VirtualFile = virtualFile

    override fun getComponent(): JComponent = viewComponent

    override fun getPreferredFocusedComponent(): JComponent = viewComponent

    private inner class FileChangedListener(var isEnabled: Boolean = true) : BulkFileListener {
        override fun after(events: MutableList<out VFileEvent>) {
            if (!isEnabled) return
            if (events.any { it.file == virtualFile }) {
                logger.info("Target file ${virtualFile.path} changed. Reloading current view.")
                webViewWindow.reload()
            }
        }
    }

    override fun dispose() {
        messageBusConnection.disconnect()
        webViewWindow.dispose()
    }

    companion object {
        private const val NAME = "Java Profile Viewer"
        private val logger = Logger.getLogger("JFRFileEditor")
    }
}
