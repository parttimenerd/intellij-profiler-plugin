package me.bechberger.jfrplugin.editor

import com.intellij.openapi.fileEditor.AsyncFileEditorProvider
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import me.bechberger.jfrplugin.lang.JsonGzFileType
import java.util.zip.GZIPInputStream

class JsonGzFileEditorProvider : AsyncFileEditorProvider, DumbAware {
    override fun getEditorTypeId() = "Firefox Profiler"

    override fun accept(project: Project, file: VirtualFile): Boolean {
        if (file.fileType != JsonGzFileType || !file.exists()) {
            return false
        }
        val wordsToLookFor = mutableSetOf(
            "\"name\"",
            "\"startTime\"",
            "\"endTime\"",
            "\"duration\"",
            "\"category\"",
            "\"meta\"",
            "\"threads\"",
            "\"version\"",
            "\"interval\"",
            "\"processType\"",
            "\"product\"",
            "\"stackWalk\"",
            "\"symbolicated\""
        )
        GZIPInputStream(file.inputStream).bufferedReader().use { reader ->
            while (wordsToLookFor.isNotEmpty()) {
                val line = reader.readLine() ?: return false
                wordsToLookFor.removeIf { line.contains(it) }
            }
        }
        return true
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
