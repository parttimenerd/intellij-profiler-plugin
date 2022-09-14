package me.bechberger.jfrplugin.settings

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.ui.layout.panel
import com.intellij.xml.util.XmlStringUtil
import me.bechberger.jfrplugin.editor.WebViewWindow
import me.bechberger.jfrplugin.util.JFRPluginBundle
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel

class JFRPluginSettingsForm(private val project: Project) : JPanel() {
    private val settings
        get() = JFRPluginSettings.getInstance(project)

    private val enableDocumentAutoReloadCheckBox = JCheckBox(
        JFRPluginBundle.message("jfr.plugin.settings.reload.document"),
        settings.enableDocumentAutoReload
    )

    val enableDocumentAutoReload
        get() = enableDocumentAutoReloadCheckBox.isSelected

    private val jfrConfigField: ExpandableTextField = ExpandableTextField()
    private val converterConfigField: JBTextField = JBTextField()
    private val jfrFileChooser: TextFieldWithBrowseButton = TextFieldWithBrowseButton()

    fun loadSettings() {
        enableDocumentAutoReloadCheckBox.isSelected = settings.enableDocumentAutoReload
        jfrConfigField.text = settings.additionalJFRConfig
        converterConfigField.text = settings.additionalConverterConfig
        jfrFileChooser.text = settings.jfrFile
    }

    val additionalJFRConfig
        get() = jfrConfigField.text
    val additionalConverterConfig
        get() = jfrConfigField.text
    val jfrFile
        get() = jfrFileChooser.text

    init {

        ComponentValidator(project).withValidator { ->
            if (jfrFileChooser.text.isEmpty()) {
                ValidationInfo(JFRPluginBundle.message("jfr.plugin.settings.jfr.file.not.set"), jfrFileChooser)
            } else if (!jfrFileChooser.text.endsWith(".jfr")) {
                ValidationInfo(JFRPluginBundle.message("jfr.plugin.settings.jfr.file.not.jfr"), jfrFileChooser)
            } else {
                null
            }
        }.installOn(jfrFileChooser)

        ComponentValidator(project).withValidator { ->
            try {
                WebViewWindow.tryToParseConfig(jfrConfigField.text)
                null
            } catch (e: Exception) {
                ValidationInfo(e.message!!, jfrConfigField)
            }
        }.installOn(converterConfigField)

        layout = BorderLayout()
        add(
            panel {
                titledRow(JFRPluginBundle.message("jfr.plugin.settings.general")) {
                    row {
                        checkBox(
                            JFRPluginBundle.message("jfr.plugin.settings.reload.document"),
                            enableDocumentAutoReload
                        )
                    }
                    row {
                        label(JFRPluginBundle.message("jfr.plugin.settings.jfr.file"))
                        jfrFileChooser()
                    }
                }
                titledRow(JFRPluginBundle.message("jfr.plugin.settings.jfr")) {
                    row {
                        add(
                            JBLabel(
                                XmlStringUtil.wrapInHtml(
                                    JFRPluginBundle.message("jfr.plugin.settings.jfr.description")
                                )
                            )
                        )
                    }
                    row {
                        JPanel(GridBagLayout()).apply {
                            GridBagConstraints().also {
                                it.anchor = GridBagConstraints.LINE_START
                                it.ipadx = 8
                                add(JLabel(JFRPluginBundle.message("jfr.plugin.settings.jfr.config")), it)
                                add(jfrConfigField, it)
                                val description = JBLabel(
                                    XmlStringUtil.wrapInHtml(
                                        JFRPluginBundle.message("jfr.plugin.settings.jfr.config.description")
                                    )
                                )
                                description.setForeground(JBColor(Gray._50, Gray._130))
                                add(description, it)
                            }
                        }()
                    }
                }
                titledRow(JFRPluginBundle.message("jfr.plugin.settings.converter")) {
                    row {
                        add(
                            JBLabel(
                                XmlStringUtil.wrapInHtml(
                                    JFRPluginBundle.message("jfr.plugin.settings.converter.description")
                                )
                            )
                        )
                    }
                    row {
                        JPanel(GridBagLayout()).apply {
                            GridBagConstraints().also {
                                it.anchor = GridBagConstraints.LINE_START
                                it.ipadx = 8
                                add(JLabel(JFRPluginBundle.message("jfr.plugin.settings.converter.config")), it)
                                add(jfrConfigField, it)
                                val description = JBLabel(
                                    XmlStringUtil.wrapInHtml(
                                        JFRPluginBundle.message("jfr.plugin.settings.converter.config.description")
                                    )
                                )
                                description.setForeground(JBColor(Gray._50, Gray._130))
                                add(description, it)
                            }
                        }()
                    }
                }
            }
        )
        loadSettings()
    }
}
