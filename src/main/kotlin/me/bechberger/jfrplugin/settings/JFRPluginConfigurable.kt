package me.bechberger.jfrplugin.settings

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import javax.swing.JComponent

class JFRPluginConfigurable(val project: Project) : SearchableConfigurable {

    private var settingsForm: JFRPluginSettingsForm? = null
    private val settings: JFRPluginSettings

    init {
        settings = JFRPluginSettings.getInstance(project)
    }

    override fun isModified(): Boolean {
        return settingsForm?.run {
            settings.enableDocumentAutoReload != enableDocumentAutoReload ||
                settings.jfrFile != jfrFile ||
                settings.additionalJFRConfig != (additionalJFRConfig ?: "") ||
                settings.additionalConverterConfig != (additionalConverterConfig ?: "")
        } ?: false
    }

    override fun getDisplayName(): String = "Java Profiler Settings"

    override fun getId(): String {
        return "Java Profiler"
    }

    override fun apply() {
        val wasModified = isModified
        settings.run {
            enableDocumentAutoReload = settingsForm?.enableDocumentAutoReload ?: enableDocumentAutoReload
            jfrFile = settingsForm?.jfrFile ?: jfrFile
            additionalJFRConfig = settingsForm?.additionalJFRConfig ?: additionalJFRConfig
            additionalConverterConfig = settingsForm?.additionalConverterConfig ?: additionalConverterConfig
        }
        if (wasModified) {
            settings.notifyListeners()
        }
    }

    override fun reset() {
        settingsForm?.loadSettings()
    }

    override fun createComponent(): JComponent? {
        settingsForm = settingsForm ?: JFRPluginSettingsForm(project)
        return settingsForm
    }

    override fun disposeUIResources() {
        settingsForm = null
    }

    companion object {
        init {
            // SearchableOptionsRegistrar.getInstance().addOption("Java Profiler", null, "Java Profiler", "Java Profiler")
        }
    }
}
