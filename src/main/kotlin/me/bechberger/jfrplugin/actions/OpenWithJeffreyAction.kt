package me.bechberger.jfrplugin.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import me.bechberger.jfrplugin.editor.JFRFileEditor
import me.bechberger.jfrplugin.lang.JFRFileType
import me.bechberger.jfrplugin.viewer.JeffreyLauncher

class OpenWithJeffreyAction : AnAction("Open with Jeffrey") {

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = file?.fileType == JFRFileType
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val url = JeffreyLauncher.startAndGetUrl(file.toNioPath()) ?: run {
            com.intellij.openapi.ui.Messages.showErrorDialog(
                project,
                "Jeffrey is not available. Run './gradlew downloadJeffrey' to install it.",
                "Jeffrey Not Found"
            )
            return
        }
        // If the file is already open in a JFRFileEditor, switch that editor to Jeffrey
        val editors = FileEditorManager.getInstance(project).getEditors(file)
        val jfrEditor = editors.filterIsInstance<JFRFileEditor>().firstOrNull()
        if (jfrEditor != null) {
            jfrEditor.webViewWindow.loadUrl(url)
        } else {
            // Open the file first, then load Jeffrey URL
            FileEditorManager.getInstance(project).openFile(file, true)
            val opened = FileEditorManager.getInstance(project).getEditors(file)
                .filterIsInstance<JFRFileEditor>().firstOrNull()
            opened?.webViewWindow?.loadUrl(url)
        }
    }
}
