package me.bechberger.jfrplugin.attach

import com.intellij.openapi.project.Project
import com.sun.tools.attach.VirtualMachine
import me.bechberger.jfrplugin.config.profilerConfig
import java.nio.file.Path
import java.util.logging.Logger
import javax.management.JMX
import javax.management.ObjectName
import javax.management.remote.JMXConnector
import javax.management.remote.JMXConnectorFactory
import javax.management.remote.JMXServiceURL

object JfrAttachEngine {
    private val LOG = Logger.getLogger("JfrAttachEngine")

    private val recordingIds = mutableMapOf<String, Long>()
    private val jmxConnections = mutableMapOf<String, JMXConnector>()
    private val frBeans = mutableMapOf<String, jdk.management.jfr.FlightRecorderMXBean>()

    fun start(pid: String, outputFile: Path, project: Project) {
        val settingsName = project.profilerConfig.jfrConfig.settings.let {
            // strip path — FlightRecorderMXBean.setPredefinedConfiguration only accepts "profile" / "default"
            if (it.contains('/') || it.contains('\\')) "profile" else it
        }

        val vm = VirtualMachine.attach(pid)
        try {
            vm.startLocalManagementAgent()
            val jmxUrl = vm.agentProperties.getProperty("com.sun.management.jmxremote.localConnectorAddress")
                ?: error("Could not obtain JMX connector URL for PID $pid")

            val jmxConn = JMXConnectorFactory.connect(JMXServiceURL(jmxUrl))
            val mbsc = jmxConn.mBeanServerConnection
            val frBean = JMX.newMXBeanProxy(
                mbsc,
                ObjectName("jdk.management.jfr:type=FlightRecorder"),
                jdk.management.jfr.FlightRecorderMXBean::class.java
            )

            val recId = frBean.newRecording()
            // Use a predefined configuration ("profile" or "default") so all standard
            // events including jdk.ExecutionSample are enabled with correct periods.
            frBean.setPredefinedConfiguration(recId, settingsName)
            frBean.setRecordingOptions(recId, mapOf("name" to "intellij-attach-$pid"))
            // Enable CPU-time profiling on Java 25+ Linux targets (JEP 509, Linux only, experimental).
            val targetProps = vm.systemProperties
            val targetMajorVersion = targetProps.getProperty("java.vm.specification.version")
                ?.toIntOrNull() ?: 0
            val targetIsLinux = targetProps.getProperty("os.name", "").lowercase().contains("linux")
            if (targetMajorVersion >= 25 && targetIsLinux) {
                frBean.setEventSettings(recId, mapOf("jdk.CPUTimeSample#enabled" to "true"))
            }
            frBean.startRecording(recId)

            recordingIds[pid] = recId
            jmxConnections[pid] = jmxConn
            frBeans[pid] = frBean
            LOG.info("JFR recording $recId started on PID $pid (config=$settingsName) → $outputFile")
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
}
