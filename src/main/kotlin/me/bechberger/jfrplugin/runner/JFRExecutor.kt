package me.bechberger.jfrplugin.runner

import com.intellij.execution.Executor
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

class JFRExecutor : Executor() {

    override fun getToolWindowId(): String {
        return this.id
    }

    override fun getToolWindowIcon(): Icon {
        return AllIcons.Toolwindows.ToolWindowRun
    }

    override fun getIcon(): Icon {
        return IconLoader.getIcon("/images/profile.png", this.javaClass)
    }

    override fun getDisabledIcon(): Icon {
        return AllIcons.Plugins.Disabled
    }

    override fun getDescription(): String {
        return "Profile application with JFR"
    }

    override fun getActionName(): String {
        return "Profile application"
    }

    override fun getId(): String {
        return EXECUTOR_ID
    }

    override fun getStartActionText(): String {
        return "Profile"
    }

    override fun getContextActionId(): String {
        return this.id + " context-action-does-not-exist"
    }

    override fun getHelpId(): String? {
        return null
    }

    companion object {

        const val EXECUTOR_ID = "Java Profile Executor"
    }
}
