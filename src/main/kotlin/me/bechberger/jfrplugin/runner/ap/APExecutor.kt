package me.bechberger.jfrplugin.runner.ap

import com.intellij.execution.Executor
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import me.bechberger.jfrplugin.util.isAsyncProfilerSupported
import javax.swing.Icon

class APExecutor : Executor() {

    override fun getToolWindowId(): String {
        return this.id
    }

    override fun getToolWindowIcon(): Icon {
        return AllIcons.Toolwindows.ToolWindowRun
    }

    override fun getIcon(): Icon {
        return IconLoader.getIcon("/images/profile_ap.png", this.javaClass)
    }

    override fun getDisabledIcon(): Icon {
        return AllIcons.Plugins.Disabled
    }

    override fun getDescription(): String {
        return "Profile application with async-profiler"
    }

    override fun getActionName(): String {
        return "Profile application (AP)"
    }

    override fun getId(): String {
        return EXECUTOR_ID
    }

    override fun getStartActionText(): String {
        return "Profile with async-profiler"
    }

    override fun getContextActionId(): String {
        return this.id + " context-action-does-not-exist"
    }

    override fun getHelpId(): String? {
        return null
    }

    override fun isApplicable(project: Project): Boolean {
        return isAsyncProfilerSupported()
    }

    companion object {

        const val EXECUTOR_ID = "AP Profile Executor"
    }
}
