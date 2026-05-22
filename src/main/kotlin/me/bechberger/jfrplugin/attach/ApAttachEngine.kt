package me.bechberger.jfrplugin.attach

import com.intellij.openapi.project.Project
import me.bechberger.jfrplugin.config.profilerConfig
import one.profiler.AsyncProfilerLoader
import java.nio.file.Path
import java.util.logging.Logger

object ApAttachEngine {
    private val LOG = Logger.getLogger("ApAttachEngine")

    fun start(pid: String, outputFile: Path, project: Project) {
        val apPath = AsyncProfilerLoader.getAsyncProfilerPath().toAbsolutePath().toString()
        val conf = project.profilerConfig.asyncProfilerConfig
        val cmd = buildString {
            append("start,event=${conf.event},loglevel=WARN")
            append(",file=${outputFile.toAbsolutePath()}")
            if (conf.alloc) append(",alloc=512k")
            if (conf.jfrsync) append(",jfrsync")
            append(",jfr")
            if (conf.misc.isNotBlank()) append(",${conf.misc}")
        }
        jattach(pid, "load", apPath, "true", cmd)
        LOG.info("async-profiler started on PID $pid → $outputFile")
    }

    fun stop(pid: String, outputFile: Path) {
        val apPath = AsyncProfilerLoader.getAsyncProfilerPath().toAbsolutePath().toString()
        val stopCmd = "stop,file=${outputFile.toAbsolutePath()}"
        jattach(pid, "load", apPath, "true", stopCmd)
        LOG.info("async-profiler stopped on PID $pid, output: $outputFile")
    }

    /** Run jattach via the built-in ap-loader jattach wrapper. */
    private fun jattach(pid: String, vararg args: String) {
        val result = AsyncProfilerLoader.executeJattach(pid, *args)
        LOG.fine("jattach stdout: ${result.stdout}")
        if (result.stderr.isNotBlank()) {
            error("jattach failed: ${result.stderr}")
        }
    }
}
