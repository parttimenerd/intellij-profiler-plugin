package me.bechberger.jfrplugin.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import me.bechberger.jfrplugin.lang.JFRFileType
import me.bechberger.jfrplugin.viewer.JeffreyLauncher

class JeffreyFileEditorProvider : FileEditorProvider, DumbAware {
    override fun getEditorTypeId() = "Jeffrey Profile"

    override fun accept(project: Project, file: VirtualFile): Boolean =
        file.fileType == JFRFileType && JeffreyLauncher.isJdkAvailable()

    override fun createEditor(project: Project, file: VirtualFile): FileEditor =
        JeffreyFileEditor(project, file)

    override fun getPolicy() = FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR
}
