package me.bechberger.jfrplugin.settings

fun interface JFRPluginSettingsListener {
    fun settingsChanged(settings: JFRPluginSettings)
}
