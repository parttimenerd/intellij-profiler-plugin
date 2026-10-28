package me.bechberger.jfrplugin.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import me.bechberger.jfrplugin.config.ViewerType
import me.bechberger.jfrplugin.config.profilerConfig
import me.bechberger.jfrplugin.lang.JFRFileType

class JFRFileOpenListener(private val project: Project) : FileEditorManagerListener {
    override fun fileOpenedSync(
        source: FileEditorManager,
        file: VirtualFile,
        editorsWithProviders: com.intellij.openapi.util.Pair<Array<FileEditor>, Array<FileEditorProvider>>
    ) {
        if (file.fileType != JFRFileType) return
        val defaultViewer = project.profilerConfig.defaultViewer
        val targetEditorId = when (defaultViewer) {
            ViewerType.JEFFREY -> "Jeffrey Profile"
            ViewerType.FIREFOX_PROFILER -> "Java Profile"
        }
        source.setSelectedEditor(file, targetEditorId)
    }
}
