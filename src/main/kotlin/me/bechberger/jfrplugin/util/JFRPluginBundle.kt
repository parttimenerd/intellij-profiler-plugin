package me.bechberger.jfrplugin.util

import com.intellij.AbstractBundle
import java.util.ResourceBundle
import org.jetbrains.annotations.PropertyKey

object JFRPluginBundle {
    private const val BUNDLE_NAME = "messages.JFRPluginBundle"

    private val bundle by lazy { ResourceBundle.getBundle(BUNDLE_NAME) }

    fun message(@PropertyKey(resourceBundle = BUNDLE_NAME) key: String, vararg params: Any): String {
        return AbstractBundle.message(bundle, key, params)
    }
}
