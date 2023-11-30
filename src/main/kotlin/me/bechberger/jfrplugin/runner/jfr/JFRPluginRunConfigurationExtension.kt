package me.bechberger.jfrplugin.runner.jfr

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import me.bechberger.jfrplugin.config.jfrFile
import me.bechberger.jfrplugin.config.jfrSettingsFile
import me.bechberger.jfrplugin.runner.BasePluginRunConfigurationExtension

/**
 * The class is used to extend the Run Configuration with the JFR specific settings.
 */
class JFRPluginRunConfigurationExtension : BasePluginRunConfigurationExtension("JFR", JFRExecutor.EXECUTOR_ID) {
    override fun computeVmParameters(project: Project): List<String> {
        val vmParametersList = mutableListOf<String>()
        vmParametersList.add("-XX:+UnlockDiagnosticVMOptions")
        vmParametersList.add("-XX:+DebugNonSafepoints")
        vmParametersList.add(
            "-XX:StartFlightRecording=filename=${project.jfrFile}," +
                "settings='${project.jfrSettingsFile}',dumponexit=true",
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
