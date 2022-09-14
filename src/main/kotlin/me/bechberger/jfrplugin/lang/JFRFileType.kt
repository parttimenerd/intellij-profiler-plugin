package me.bechberger.jfrplugin.lang

import com.intellij.openapi.fileTypes.FileType
import me.bechberger.jfrplugin.util.JFRPluginIcons

object JFRFileType : FileType {
    override fun getIcon() = JFRPluginIcons.MAIN_ICON

    override fun getName() = "JFR"

    override fun getDefaultExtension() = "jfr"

    override fun getDescription() = "Java Profile"

    override fun isBinary() = true
}
