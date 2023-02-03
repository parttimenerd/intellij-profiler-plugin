package me.bechberger.jfrplugin.runner.ap

import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import me.bechberger.jfrplugin.util.isAsyncProfilerSupported
import org.jdom.Element

/**
 * The class is used to extend the Run Configuration with the async-profiler specific settings.
 */
class APPluginRunConfigurationExtension : RunConfigurationExtension() {

    override fun readExternal(configuration: RunConfigurationBase<*>, element: Element) = Unit

    override fun writeExternal(configuration: RunConfigurationBase<*>, element: Element) = Unit

    override fun getEditorTitle(): String {
        return "AP"
    }

    override fun isEnabledFor(configuration: RunConfigurationBase<*>, runnerSettings: RunnerSettings?): Boolean {
        return isAsyncProfilerSupported()
    }

    override fun <T : RunConfigurationBase<*>> updateJavaParameters(
        configuration: T,
        params: JavaParameters,
        runnerSettings: RunnerSettings?
    ) = Unit

    override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean {
        return isEnabledFor(configuration, null)
    }
}
