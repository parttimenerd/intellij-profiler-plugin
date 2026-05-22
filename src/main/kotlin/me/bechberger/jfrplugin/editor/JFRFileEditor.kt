package me.bechberger.jfrplugin.editor

import com.intellij.diff.util.FileEditorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import me.bechberger.jfrplugin.config.profilerConfig
import javax.swing.JComponent
import java.util.logging.Logger

class JFRFileEditor(private val project: Project, private val virtualFile: VirtualFile) : FileEditorBase() {
    val webViewWindow: WebViewWindow
    private val messageBusConnection = project.messageBus.connect()
    private val fileChangedListener = FileChangedListener()

    init {
        if (!virtualFile.exists() || !(virtualFile.path.endsWith(".jfr") || virtualFile.path.endsWith(".json.gz"))) {
            throw IllegalArgumentException("File must exist and have .jfr or .json.gz extension")
        }
        messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, fileChangedListener)
        webViewWindow = WebViewWindow(
            project,
            project.profilerConfig.conversionConfig.toConfig(),
            virtualFile.toNioPath()
        )
    }

    override fun getName(): String = NAME
    override fun getFile(): VirtualFile = virtualFile
    override fun getComponent(): JComponent = webViewWindow.component
    override fun getPreferredFocusedComponent(): JComponent = webViewWindow.component

    private inner class FileChangedListener : BulkFileListener {
        override fun after(events: MutableList<out VFileEvent>) {
            if (events.any { it.file == virtualFile }) {
                webViewWindow.reload()
            }
        }
    }

    override fun dispose() {
        messageBusConnection.disconnect()
        webViewWindow.dispose()
    }

    companion object {
        const val NAME = "Java Profile Viewer"
        private val logger = Logger.getLogger("JFRFileEditor")
    }
}
