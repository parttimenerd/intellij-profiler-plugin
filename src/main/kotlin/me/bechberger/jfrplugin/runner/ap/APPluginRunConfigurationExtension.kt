package me.bechberger.jfrplugin.runner.ap

import com.intellij.execution.Executor
import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.openapi.project.Project
import me.bechberger.jfrplugin.config.jfrFile
import me.bechberger.jfrplugin.config.profilerConfig
import me.bechberger.jfrplugin.util.isAsyncProfilerSupported
import one.profiler.AsyncProfilerLoader
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

    override fun <T : RunConfigurationBase<*>?> updateJavaParameters(
        configuration: T & Any,
        params: JavaParameters,
        runnerSettings: RunnerSettings?,
        executor: Executor
    ) {
        if (executor.id == APExecutor.EXECUTOR_ID) {
            params.vmParametersList.addAll(computeVmParameters(configuration.project))
        }
    }

    override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean {
        return isEnabledFor(configuration, null)
    }

    companion object {
        fun computeVmParameters(project: Project): List<String> {
            val vmParametersList = mutableListOf<String>()
            vmParametersList.add("-XX:+UnlockDiagnosticVMOptions")
            vmParametersList.add("-XX:+DebugNonSafepoints")
            val asyncProfiler = AsyncProfilerLoader.getAsyncProfilerPath()
            val conf = project.profilerConfig.asyncProfilerConfig
            vmParametersList.add(
                "-agentpath:$asyncProfiler=start,event=${conf.event},loglevel=WARN," +
                    "file=${project.jfrFile}" +
                    (if (conf.alloc) ",alloc=512k" else "") +
                    "${if (conf.jfrsync) ",jfrsync" else ""}," +
                    "jfr${if (conf.misc.isNotBlank()) ",${conf.misc}" else ""}"
            )
            return vmParametersList
        }
    }
}
