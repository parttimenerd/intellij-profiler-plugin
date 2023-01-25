// based on a class by Rastislav Papp (rastislav.papp@gmail.com)
package me.bechberger.jfrplugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import java.nio.file.Path

/**
 * Some constants (and related utility functions) that are used throughout the plugin
 */
object Constants {

    const val DEFAULT_JFR_FILENAME = "profile.jfr"

    fun getJFRFile(project: Project): Path {
        return Path.of(project.guessProjectDir()!!.canonicalPath!!).resolve(DEFAULT_JFR_FILENAME)
    }

    fun getJFRVirtualFile(project: Project): VirtualFile {
        return VirtualFileManager.getInstance().findFileByUrl("file://${getJFRFile(project)}")!!
    }
}
