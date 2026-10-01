package me.bechberger.jfrplugin.runner.ap

import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.openapi.project.Project
import me.bechberger.jfrplugin.config.jfrFile
import me.bechberger.jfrplugin.config.profilerConfig
import me.bechberger.jfrplugin.mcp.McpBridge
import me.bechberger.jfrplugin.runner.BasePluginRunConfigurationExtension
import me.bechberger.jfrplugin.util.isAsyncProfilerSupported
import one.profiler.AsyncProfilerLoader

/**
 * The class is used to extend the Run Configuration with the async-profiler specific settings.
 */
class APPluginRunConfigurationExtension : BasePluginRunConfigurationExtension("AP", APExecutor.EXECUTOR_ID) {
    override fun isEnabledFor(configuration: RunConfigurationBase<*>, runnerSettings: RunnerSettings?): Boolean {
        return isAsyncProfilerSupported() && super.isApplicableFor(configuration)
    }

    override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean {
        return isEnabledFor(configuration, null)
    }

    override fun computeVmParameters(project: Project): List<String> {
        val vmParametersList = mutableListOf<String>()
        vmParametersList.add("-XX:+UnlockDiagnosticVMOptions")
        vmParametersList.add("-XX:+DebugNonSafepoints")
        val asyncProfiler = AsyncProfilerLoader.getAsyncProfilerPath()
        val conf = project.profilerConfig.asyncProfilerConfig
        // MCP may override the event type for this run
        val event = McpBridge.apEventOverride.get() ?: conf.event
        val includeAlloc = conf.alloc && event != "alloc"
        vmParametersList.add(
            "-agentpath:$asyncProfiler=start,event=$event,loglevel=WARN," +
                    "file=${project.jfrFile}" +
                    (if (includeAlloc) ",alloc=512k" else "") +
                    "${if (conf.jfrsync) ",jfrsync" else ""}," +
                    "jfr${if (conf.misc.isNotBlank()) ",${conf.misc}" else ""}"
        )
        return vmParametersList
    }
}
