package me.bechberger.jfrplugin.attach

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.sun.tools.attach.VirtualMachine
import com.sun.tools.attach.VirtualMachineDescriptor
import me.bechberger.jfrplugin.config.profilerConfig
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

enum class Engine { JFR, ASYNC_PROFILER }

data class JvmInfo(
    val pid: String,
    val displayName: String,
)

sealed class RecordingState {
    object Idle : RecordingState()
    data class Recording(val engine: Engine, val outputFile: Path) : RecordingState()
}

@Service(Service.Level.PROJECT)
class AttachService(private val project: Project) {

    private val states = ConcurrentHashMap<String, RecordingState>()

    fun listJvms(): List<JvmInfo> {
        return try {
            VirtualMachine.list().map { d: VirtualMachineDescriptor ->
                JvmInfo(d.id(), label(d))
            }.filter { it.pid != myPid }
        } catch (e: Exception) {
            LOG.warning("Could not list JVMs: ${e.message}")
            emptyList()
        }
    }

    fun state(pid: String): RecordingState = states[pid] ?: RecordingState.Idle

    fun start(pid: String, engine: Engine) {
        check(state(pid) is RecordingState.Idle) { "Already recording on pid $pid" }
        val outputFile = project.profilerConfig.getJFRFile(project)
        when (engine) {
            Engine.JFR -> JfrAttachEngine.start(pid, outputFile, project)
            Engine.ASYNC_PROFILER -> ApAttachEngine.start(pid, outputFile, project)
        }
        states[pid] = RecordingState.Recording(engine, outputFile)
    }

    fun stop(pid: String): Path {
        val st = state(pid) as? RecordingState.Recording
            ?: error("Not recording on pid $pid")
        when (st.engine) {
            Engine.JFR -> JfrAttachEngine.stop(pid, st.outputFile)
            Engine.ASYNC_PROFILER -> ApAttachEngine.stop(pid, st.outputFile)
        }
        states[pid] = RecordingState.Idle
        return st.outputFile
    }

    companion object {
        private val LOG = Logger.getLogger("AttachService")
        private val myPid = ProcessHandle.current().pid().toString()

        fun getInstance(project: Project): AttachService = project.service()

        private fun label(d: VirtualMachineDescriptor): String {
            val name = d.displayName()
            return if (name.isNullOrBlank()) "PID ${d.id()}" else "${d.id()} – $name"
        }
    }
}
