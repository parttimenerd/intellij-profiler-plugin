package me.bechberger.jfrplugin.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.messages.Topic
import com.intellij.util.xmlb.XmlSerializerUtil.copyBean

@State(name = "JFRPluginSettings", storages = [(Storage("java_profiler.xml"))])
class JFRPluginSettings : PersistentStateComponent<JFRPluginSettings> {
    var additionalJFRConfig: String = ""
    var additionalConverterConfig: String = ""
    var enableDocumentAutoReload = true
    var jfrFile = "profile.jfr"

    fun notifyListeners() {
        ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).settingsChanged(this)
    }

    override fun getState() = this

    override fun loadState(state: JFRPluginSettings) {
        copyBean(state, this)
    }

    companion object {
        val TOPIC = Topic(JFRPluginSettingsListener::class.java)

        fun getInstance(project: Project): JFRPluginSettings = project.getService(JFRPluginSettings::class.java) ?: JFRPluginSettings()

        fun getJFRFile(project: Project): VirtualFile? {
            val file = project.basePath + "/" + getInstance(project).jfrFile
            return VirtualFileManager.getInstance().findFileByUrl("file://$file")
        }
    }
}
