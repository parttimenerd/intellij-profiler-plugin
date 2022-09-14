package me.bechberger.jfrplugin.editor

import com.intellij.openapi.fileEditor.AsyncFileEditorProvider
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import me.bechberger.jfrplugin.lang.JFRFileType

class JFRFileEditorProvider : AsyncFileEditorProvider, DumbAware {
    override fun getEditorTypeId() = "Java Profile"

    override fun accept(project: Project, file: VirtualFile): Boolean {
        return file.fileType == JFRFileType
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return createEditorAsync(project, file).build()
    }

    override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR

    override fun createEditorAsync(project: Project, file: VirtualFile): AsyncFileEditorProvider.Builder {
        return object : AsyncFileEditorProvider.Builder() {
            override fun build(): FileEditor = JFRFileEditor(project, file)
        }
    }
}
