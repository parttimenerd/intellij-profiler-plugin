package me.bechberger.jfrplugin.viewer

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightVirtualFile
import me.bechberger.jfrplugin.editor.JeffreyUrlFileType

/**
 * Opens a new Jeffrey browser tab pointing at [url].
 * Each call creates a new synthetic virtual file so multiple tabs can coexist.
 */
object JeffreyBrowserWindowOpener {
    fun openInNewTab(project: Project, url: String) {
        val vf = JeffreyUrlVirtualFile(url)
        FileEditorManager.getInstance(project).openFile(vf, true)
    }
}

/** A lightweight in-memory virtual file whose content encodes the Jeffrey URL to open. */
class JeffreyUrlVirtualFile(val jeffreyUrl: String) :
    LightVirtualFile("Jeffrey (${jeffreyUrl.substringAfterLast('/')})", JeffreyUrlFileType, "")
