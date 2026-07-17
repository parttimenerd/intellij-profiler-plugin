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
        val majorVersion = ProjectRootManager.getInstance(project).projectSdk?.versionString
            ?.let { Regex("version \"(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 0
        val isLinux = System.getProperty("os.name", "").lowercase().contains("linux")
        val cpuTimeSampleOption = if (majorVersion >= 25 && isLinux) ",jdk.CPUTimeSample#enabled=true" else ""
        vmParametersList.add(
            "-XX:StartFlightRecording=filename=${project.jfrFile}," +
                "settings='${project.jfrSettingsFile}',dumponexit=true$cpuTimeSampleOption",
        )
        if (majorVersion == 0 || majorVersion >= 17) {
            vmParametersList.add("-Xlog:jfr+startup=error")
        }
        if (majorVersion in 8..12) {
            vmParametersList.add("-XX:+FlightRecorder")
        }
        return vmParametersList
    }
}
