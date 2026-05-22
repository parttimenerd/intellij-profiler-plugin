package me.bechberger.jfrplugin.attach

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.sun.tools.attach.VirtualMachine
import java.nio.file.Path
import java.util.logging.Logger

object JfrAttachEngine {
    private val LOG = Logger.getLogger("JfrAttachEngine")

    // We track recording IDs by pid so we can stop the right recording.
    private val recordingIds = mutableMapOf<String, Long>()

    fun start(pid: String, outputFile: Path, project: Project) {
        val vm = VirtualMachine.attach(pid)
        try {
            // Ensure the local management agent is running so we can get a JMX URL.
            vm.startLocalManagementAgent()
            val props = vm.agentProperties
            val jmxUrl = props.getProperty("com.sun.management.jmxremote.localConnectorAddress")
                ?: error("Could not obtain JMX connector URL for PID $pid")

            val jmxConn = javax.management.remote.JMXConnectorFactory.connect(
                javax.management.remote.JMXServiceURL(jmxUrl)
            )
            val mbsc = jmxConn.mBeanServerConnection
            val frBean = javax.management.JMX.newMXBeanProxy(
                mbsc,
                javax.management.ObjectName("jdk.management.jfr:type=FlightRecorder"),
                jdk.management.jfr.FlightRecorderMXBean::class.java
            )

            val recId = frBean.newRecording()
            val settingsMap = buildSettingsMap(project)
            frBean.setRecordingSettings(recId, settingsMap)
            frBean.startRecording(recId)
            recordingIds[pid] = recId
            LOG.info("JFR recording $recId started on PID $pid → $outputFile")

            // Store the JMX connection for use in stop()
            jmxConnections[pid] = jmxConn
            frBeans[pid] = frBean
        } finally {
            vm.detach()
        }
    }

    fun stop(pid: String, outputFile: Path) {
        val recId = recordingIds.remove(pid) ?: error("No active JFR recording for PID $pid")
        val frBean = frBeans.remove(pid) ?: error("No FlightRecorderMXBean for PID $pid")
        val jmxConn = jmxConnections.remove(pid)

        try {
            frBean.stopRecording(recId)
            frBean.copyTo(recId, outputFile.toAbsolutePath().toString())
            frBean.closeRecording(recId)
            LOG.info("JFR recording $recId stopped and dumped to $outputFile")
        } finally {
            jmxConn?.close()
        }
    }

    private fun buildSettingsMap(project: Project): Map<String, String> {
        // Use the same "profile" or "default" settings as configured for regular profiling.
        val settingsName = try {
            project.getService(com.intellij.openapi.project.Project::class.java)
                ?.let { me.bechberger.jfrplugin.config.ProfilerConfig.get(it).jfrConfig.settings }
                ?: "profile"
        } catch (_: Exception) { "profile" }

        return mapOf(
            "jdk.CPUSample#enabled" to "true",
            "jdk.CPUSample#period" to "10 ms",
            "jdk.JavaMonitorWait#enabled" to "true",
            "jdk.ThreadPark#enabled" to "true",
            "disk" to settingsName,
        )
    }

    private val jmxConnections = mutableMapOf<String, javax.management.remote.JMXConnector>()
    private val frBeans = mutableMapOf<String, jdk.management.jfr.FlightRecorderMXBean>()
}
