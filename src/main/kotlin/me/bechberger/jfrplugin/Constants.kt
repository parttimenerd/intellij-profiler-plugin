// based on a class by Rastislav Papp (rastislav.papp@gmail.com)
package me.bechberger.jfrplugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager

/**
 * Some constants (and related utility functions) that are used throughout the plugin
 */
object Constants {

    const val DEFAULT_JFR_FILENAME = "profile.jfr"
    const val JFR_TOOL_WINDOW_ID = "JFR"

    fun getJFRFile(project: Project): VirtualFile {
        val file = project.basePath + "/" + DEFAULT_JFR_FILENAME
        return VirtualFileManager.getInstance().findFileByUrl("file://$file")!!
    }
}
