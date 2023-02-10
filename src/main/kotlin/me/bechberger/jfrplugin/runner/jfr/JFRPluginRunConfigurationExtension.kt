package me.bechberger.jfrplugin.runner.jfr

import com.intellij.execution.Executor
import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import me.bechberger.jfrplugin.config.jfrFile
import me.bechberger.jfrplugin.config.jfrSettingsFile
import org.jdom.Element

/**
 * The class is used to extend the Run Configuration with the JFR specific settings.
 */
class JFRPluginRunConfigurationExtension : RunConfigurationExtension() {

    override fun readExternal(configuration: RunConfigurationBase<*>, element: Element) = Unit

    override fun writeExternal(configuration: RunConfigurationBase<*>, element: Element) = Unit

    override fun getEditorTitle(): String {
        return "JFR"
    }

    override fun isEnabledFor(configuration: RunConfigurationBase<*>, runnerSettings: RunnerSettings?): Boolean {
        return true
    }

    override fun <T : RunConfigurationBase<*>?> updateJavaParameters(
        configuration: T & Any,
        params: JavaParameters,
        runnerSettings: RunnerSettings?
    ) {
        throw UnsupportedOperationException("Not implemented")
    }

    override fun <T : RunConfigurationBase<*>?> updateJavaParameters(
        configuration: T & Any,
        params: JavaParameters,
        runnerSettings: RunnerSettings?,
        executor: Executor
    ) {
        if (executor.id == JFRExecutor.EXECUTOR_ID) {
            params.vmParametersList.addAll(computeVmParameters(configuration.project))
        }
    }

    override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean {
        return true
    }

    companion object {
        fun computeVmParameters(project: Project): List<String> {
            val vmParametersList = mutableListOf<String>()
            vmParametersList.add("-XX:+UnlockDiagnosticVMOptions")
            vmParametersList.add("-XX:+DebugNonSafepoints")
            vmParametersList.add(
                "-XX:StartFlightRecording=filename=${project.jfrFile}," +
                    "settings='${project.jfrSettingsFile}',dumponexit=true"
            )
            ProjectRootManager.getInstance(project).projectSdk?.versionString?.let {
                if (!it.matches("(.*1[.][0-9][.].+)|(.*(8|9|10|11|12|13|14|15|16)[.][0-9]+[.][0-9]+.*)".toRegex())) {
                    vmParametersList.add("-Xlog:jfr+startup=error")
                }
                if (it.matches(".*(8|9|10|11|12)[.][0-9]+[.][0-9]+.*".toRegex())) {
                    vmParametersList.add("-XX:+FlightRecorder")
                }
            }
            return vmParametersList
        }
    }
}
