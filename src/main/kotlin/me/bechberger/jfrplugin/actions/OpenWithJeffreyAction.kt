package me.bechberger.jfrplugin.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import me.bechberger.jfrplugin.lang.JFRFileType
import me.bechberger.jfrplugin.viewer.JeffreyBrowserWindowOpener
import me.bechberger.jfrplugin.viewer.JeffreyLauncher
import java.nio.file.Path

class OpenWithJeffreyAction : AnAction("Open with Jeffrey") {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: emptyArray()
        e.presentation.isEnabledAndVisible =
            JeffreyLauncher.isJdkAvailable() && collectJfrFiles(files).isNotEmpty()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: emptyArray()
        val jfrPaths = collectJfrFiles(files)
        if (jfrPaths.isEmpty()) return

        if (jfrPaths.size == 1) {
            // Single file: open via JeffreyFileEditorProvider so the tab is tied to the file
            val vf = files.firstOrNull { it.fileType == JFRFileType }
                ?: com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                    .refreshAndFindFileByNioFile(jfrPaths.first())
                ?: return
            ApplicationManager.getApplication().invokeLater {
                val editors = com.intellij.openapi.fileEditor.FileEditorManager
                    .getInstance(project).openFile(vf, true)
                editors.filterIsInstance<me.bechberger.jfrplugin.editor.JeffreyFileEditor>()
                    .firstOrNull()?.let {
                        com.intellij.openapi.fileEditor.FileEditorManager
                            .getInstance(project).setSelectedEditor(vf, "Jeffrey Profile")
                    }
            }
        } else {
            // Multiple files / folder: upload all, open recordings list in a new tab
            ApplicationManager.getApplication().executeOnPooledThread {
                val url = JeffreyLauncher.startOrReuseAndGetUrlForFiles(jfrPaths, project)
                    ?: return@executeOnPooledThread
                ApplicationManager.getApplication().invokeLater {
                    JeffreyBrowserWindowOpener.openInNewTab(project, url)
                }
            }
        }
    }

    companion object {
        fun collectJfrFiles(files: Array<VirtualFile>): List<Path> =
            files.flatMap { it.collectJfr() }

        private fun VirtualFile.collectJfr(): List<Path> = when {
            isDirectory -> children.orEmpty().flatMap { it.collectJfr() }
            fileType == JFRFileType -> listOf(toNioPath())
            name.endsWith(".jfr") -> listOf(toNioPath())
            else -> emptyList()
        }
    }
}
