package me.bechberger.jfrplugin.viewer

import java.io.IOException
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

object JeffreyLauncher {

    private val LOG = Logger.getLogger("JeffreyLauncher")
    private var process: Process? = null
    private var currentPort: Int = -1

    /** Returns the base URL (http://localhost:<port>) once the server is ready, or null if unavailable. */
    fun startAndGetUrl(jfrFile: Path): String? {
        stop()

        val jar = extractJar() ?: run {
            LOG.warning("microscope.jar not bundled — Jeffrey viewer unavailable")
            return null
        }

        val port = findFreePort()
        val seedDir = prepareSeedDir(jfrFile) ?: return null

        val cmd = listOf(
            ProcessHandle.current().info().command().orElse("java"),
            "-jar", jar.toAbsolutePath().toString(),
            "--server.port=$port",
            "--jeffrey.microscope.seed.recordings.enabled=true",
            "--jeffrey.microscope.seed.recordings.dir=${seedDir.toAbsolutePath()}"
        )
        LOG.info("Launching Jeffrey: $cmd")

        process = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start()
        currentPort = port

        if (!waitForReady(port, timeoutSeconds = 30)) {
            LOG.warning("Jeffrey did not become ready within 30 s")
            stop()
            return null
        }

        return "http://localhost:$port"
    }

    fun stop() {
        process?.destroyForcibly()
        process = null
        currentPort = -1
    }

    fun isRunning(): Boolean = process?.isAlive == true

    private fun extractJar(): Path? {
        val resource = JeffreyLauncher::class.java.getResourceAsStream("/jeffrey/microscope.jar") ?: return null
        val dest = pluginDataDir().resolve("microscope.jar")
        Files.createDirectories(dest.parent)
        resource.use { Files.copy(it, dest, StandardCopyOption.REPLACE_EXISTING) }
        return dest
    }

    private fun prepareSeedDir(jfrFile: Path): Path? {
        return try {
            val dir = pluginDataDir().resolve("jeffrey-seed")
            Files.createDirectories(dir)
            // Clear old seeds so we only see the current file
            dir.toFile().listFiles()?.forEach { it.delete() }
            Files.copy(jfrFile, dir.resolve(jfrFile.fileName), StandardCopyOption.REPLACE_EXISTING)
            dir
        } catch (e: IOException) {
            LOG.warning("Could not prepare Jeffrey seed dir: ${e.message}")
            null
        }
    }

    private fun waitForReady(port: Int, timeoutSeconds: Int): Boolean {
        val deadline = System.currentTimeMillis() + timeoutSeconds * 1000L
        while (System.currentTimeMillis() < deadline) {
            if (process?.isAlive == false) return false
            try {
                java.net.Socket("localhost", port).use { return true }
            } catch (_: IOException) {
                TimeUnit.MILLISECONDS.sleep(300)
            }
        }
        return false
    }

    private fun findFreePort(): Int {
        ServerSocket(0).use { return it.localPort }
    }

    private fun pluginDataDir(): Path {
        val home = System.getProperty("user.home") ?: "."
        return Path.of(home, ".intellij-jfr-profiler")
    }
}
