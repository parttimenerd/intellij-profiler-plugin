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
        val versionString = ProjectRootManager.getInstance(project).projectSdk?.versionString ?: ""
        val majorVersion = Regex("version \"(\\d+)").find(versionString)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val isLinux = System.getProperty("os.name", "").lowercase().contains("linux")
        val cpuTimeSampleOption = if (majorVersion >= 25 && isLinux) ",jdk.CPUTimeSample#enabled=true" else ""
        vmParametersList.add(
            "-XX:StartFlightRecording=filename=${project.jfrFile}," +
                "settings='${project.jfrSettingsFile}',dumponexit=true$cpuTimeSampleOption",
        )
        if (!versionString.matches("(.*1[.][0-9][.].+)|(.*(8|9|10|11|12|13|14|15|16)[.][0-9]+[.][0-9]+.*)".toRegex())) {
            vmParametersList.add("-Xlog:jfr+startup=error")
        }
        if (versionString.matches(".*(8|9|10|11|12)[.][0-9]+[.][0-9]+.*".toRegex())) {
            vmParametersList.add("-XX:+FlightRecorder")
        }
        return vmParametersList
    }
}
