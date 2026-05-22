package me.bechberger.jfrplugin.editor

import com.intellij.diff.util.FileEditorBase
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import me.bechberger.jfrplugin.viewer.JeffreyUrlVirtualFile
import javax.swing.JComponent
import javax.swing.JLabel

class JeffreyUrlFileEditorProvider : FileEditorProvider, DumbAware {
    override fun getEditorTypeId() = "Jeffrey URL"
    override fun accept(project: Project, file: VirtualFile) = file is JeffreyUrlVirtualFile
    override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val urlFile = file as JeffreyUrlVirtualFile
        return JeffreyUrlFileEditor(project, urlFile)
    }
}

private class JeffreyUrlFileEditor(
    project: Project,
    private val urlFile: JeffreyUrlVirtualFile,
) : FileEditorBase() {

    private val component: JComponent

    init {
        val placeholder = JLabel("Opening Jeffrey…", JLabel.CENTER)
        component = placeholder

        Thread {
            val browser = JeffreyBrowserWindow(project, urlFile.jeffreyUrl)
            javax.swing.SwingUtilities.invokeLater {
                val parent = placeholder.parent ?: return@invokeLater
                parent.remove(placeholder)
                parent.add(browser.component)
                parent.revalidate()
                parent.repaint()
            }
        }.also { it.isDaemon = true }.start()
    }

    override fun getName() = "Jeffrey"
    override fun getFile(): VirtualFile = urlFile
    override fun getComponent() = component
    override fun getPreferredFocusedComponent() = component
}
