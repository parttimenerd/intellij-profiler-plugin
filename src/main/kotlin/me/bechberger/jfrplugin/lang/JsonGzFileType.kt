package me.bechberger.jfrplugin.lang

import com.intellij.openapi.fileTypes.FileType
import me.bechberger.jfrplugin.util.JFRPluginIcons

object JsonGzFileType : FileType {
    override fun getIcon() = JFRPluginIcons.MAIN_ICON

    override fun getName() = "Firefox Profiler"

    override fun getDefaultExtension() = "json.gz"

    override fun getDescription() = "Files that might be Firefox Profiler profiles"

    override fun isBinary() = true
}
