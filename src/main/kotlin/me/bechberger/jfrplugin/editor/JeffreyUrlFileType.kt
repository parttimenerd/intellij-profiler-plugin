package me.bechberger.jfrplugin.editor

import com.intellij.openapi.fileTypes.FileType
import javax.swing.Icon

/** Synthetic file type for Jeffrey URL tabs (not backed by a real file). */
object JeffreyUrlFileType : FileType {
    override fun getName() = "Jeffrey URL"
    override fun getDescription() = "Jeffrey profiler session"
    override fun getDefaultExtension() = ""
    override fun getIcon(): Icon? = null
    override fun isBinary() = false
}
