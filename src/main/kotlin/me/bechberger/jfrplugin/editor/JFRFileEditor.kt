package me.bechberger.jfrplugin.editor

import com.intellij.diff.util.FileEditorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import java.util.logging.Logger
import javax.swing.JComponent

class JFRFileEditor(private val project: Project, private val virtualFile: VirtualFile) : FileEditorBase() {
    val viewComponent: JComponent
    val webViewWindow: WebViewWindow
    private val messageBusConnection = project.messageBus.connect()
    private val fileChangedListener = FileChangedListener(
        true
    )

    init {
        if (!virtualFile.exists() || !(virtualFile.path.endsWith(".jfr") || virtualFile.path.endsWith(".json.gz"))) {
            throw IllegalArgumentException("File must exist and have .jfr or .json.gz extension")
        }

        messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, fileChangedListener)
        webViewWindow = WebViewWindow(
            project,
            virtualFile.toNioPath()
        )
        viewComponent = webViewWindow.component
    }

    override fun getName(): String = NAME

    override fun getFile(): VirtualFile = virtualFile

    override fun getComponent(): JComponent = viewComponent

    override fun getPreferredFocusedComponent(): JComponent = viewComponent

    private inner class FileChangedListener(var isEnabled: Boolean = true) : BulkFileListener {
        override fun after(events: MutableList<out VFileEvent>) {
            if (!isEnabled) {
                return
            }
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
