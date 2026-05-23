package me.bechberger.jfrplugin.viewer

import com.intellij.openapi.project.Project
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

    /** Minimum Java major version required to run the bundled Jeffrey jar. */
    const val JEFFREY_MIN_JAVA = 25

    /**
     * Starts Jeffrey (or reuses a running instance) and returns the `/quick-open?path=` URL
     * for [jfrFile]. Jeffrey imports and analyzes the file itself via local path — no upload needed.
     * Returns null if no suitable JDK is available or startup fails.
     */
    fun startOrReuseAndGetUrl(jfrFile: Path, project: Project): String? =
        startOrReuseAndGetUrlForFiles(listOf(jfrFile), project)

    /**
     * Starts Jeffrey (or reuses a running instance) and returns a URL for the given files.
     * Single file: uses the `/quick-open?path=` deep link (Jeffrey imports + analyzes itself).
     * Multiple files: imports each via `/from-path`, then returns the recordings list.
     */
    fun startOrReuseAndGetUrlForFiles(jfrFiles: List<Path>, project: Project): String? {
        if (jfrFiles.isEmpty()) return null
        if (!isRunning()) {
            if (!startAndGetUrl(project)) return null
        }
        val base = "http://localhost:$currentPort"
        if (jfrFiles.size == 1) {
            val encoded = java.net.URLEncoder.encode(jfrFiles.first().toAbsolutePath().toString(), "UTF-8")
            return "$base/quick-open?path=$encoded"
        }
        // Multiple files: import each by path (no file bytes sent over HTTP)
        jfrFiles.forEach { file ->
            importFromPath(file)
        }
        return "$base/recordings"
    }

    /** Tells Jeffrey to import a JFR file from its local path. Returns the recordingId or null. */
    private fun importFromPath(jfrFile: Path): String? {
        val base = "http://localhost:$currentPort"
        return try {
            val url = java.net.URI("$base/api/internal/recordings/from-path").toURL()
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 5_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("Content-Type", "application/json")
            val body = "{\"path\":\"${jfrFile.toAbsolutePath().toString().replace("\\", "\\\\")}\"}"
            conn.outputStream.use { it.write(body.toByteArray()) }

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                extractJsonString(response, "recordingId").also {
                    if (it == null) LOG.warning("Jeffrey from-path response missing recordingId: $response")
                }
            } else {
                LOG.warning("Jeffrey from-path of ${jfrFile.fileName} returned HTTP ${conn.responseCode}")
                null
            }
        } catch (e: Exception) {
            LOG.warning("Jeffrey from-path of ${jfrFile.fileName} failed: ${e.message}")
            null
        }
    }

    private fun extractJsonString(json: String, key: String): String? {
        val pattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"([^\"]+)\"")
        return pattern.find(json)?.groupValues?.get(1)
    }

    private fun startAndGetUrl(project: Project): Boolean {
        stop()

        val jar = extractJar() ?: run {
            LOG.warning("microscope.jar not bundled — Jeffrey viewer unavailable")
            return false
        }
        LOG.info("Jeffrey jar at: $jar (${Files.size(jar)} bytes)")

        val javaExe = findJava() ?: run {
            LOG.warning("No JDK $JEFFREY_MIN_JAVA+ found — Jeffrey viewer unavailable. " +
                    "Install JDK $JEFFREY_MIN_JAVA+ and set JAVA_HOME or add it to PATH.")
            return false
        }
        LOG.info("Launching Jeffrey with: $javaExe")

        val port = findFreePort()

        val ideService = JeffreyIdeJumpService.getInstance(project)
        ideService.start()
        val idePort = ideService.port

        val cmd = mutableListOf(
            javaExe,
            "-jar", jar.toAbsolutePath().toString(),
            "--server.port=$port"
        )
        if (idePort > 0) {
            cmd += "--jeffrey.microscope.ide.enabled=true"
            cmd += "--jeffrey.microscope.ide.base-url=http://localhost:$idePort"
        }
        LOG.info("Jeffrey command: $cmd")

        val logFile = pluginDataDir().resolve("jeffrey.log")
        process = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .redirectOutput(logFile.toFile())
            .start()
        currentPort = port

        // Fast-fail: if the process exits within 2 s it crashed immediately
        TimeUnit.MILLISECONDS.sleep(2000)
        if (process?.isAlive == false) {
            LOG.warning("Jeffrey process exited immediately — see ${logFile.toAbsolutePath()}")
            try { Files.readAllLines(logFile).takeLast(20).forEach { LOG.warning("Jeffrey: $it") }
            } catch (_: Exception) {}
            stop()
            return false
        }

        if (!waitForReady(port, timeoutSeconds = 60)) {
            LOG.warning("Jeffrey did not become ready within 60 s — see ${logFile.toAbsolutePath()}")
            try { Files.readAllLines(logFile).takeLast(20).forEach { LOG.warning("Jeffrey: $it") }
            } catch (_: Exception) {}
            stop()
            return false
        }

        return true
    }

    fun stop() {
        process?.destroyForcibly()
        process = null
        currentPort = -1
    }

    fun isRunning(): Boolean = process?.isAlive == true

    /** Returns true if a suitable JDK for Jeffrey is available on this machine. */
    fun isJdkAvailable(): Boolean = findJava() != null

    private fun extractJar(): Path? {
        val resource = JeffreyLauncher::class.java.getResourceAsStream("/jeffrey/microscope.jar")
            ?: return null
        val dest = pluginDataDir().resolve("microscope.jar")
        Files.createDirectories(dest.parent)
        resource.use { input ->
            val available = input.available().toLong()
            if (!Files.exists(dest) || Files.size(dest) != available) {
                Files.copy(input, dest, StandardCopyOption.REPLACE_EXISTING)
            }
        }
        return dest
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

    /**
     * Finds a java executable with major version >= [JEFFREY_MIN_JAVA].
     * Search order (mirrors execjar/launcher.sh):
     *   1. JAVA_HOME env var
     *   2. java on PATH
     *   3. SDKMAN candidates (~/.sdkman/candidates/java/&#42;)
     *   4. macOS /Library/Java/JavaVirtualMachines
     *   5. Linux /usr/lib/jvm
     * Returns null if no suitable JVM is found.
     */
    private fun findJava(): String? {
        val candidates = mutableListOf<Path>()

        // 1. JAVA_HOME
        System.getenv("JAVA_HOME")?.let { candidates.add(Path.of(it, "bin", "java")) }

        // 2. java on PATH
        runCatching {
            ProcessBuilder("which", "java").start().inputStream.bufferedReader().readLine()
        }.getOrNull()?.let { candidates.add(Path.of(it)) }

        // 3. SDKMAN
        val sdkman = Path.of(System.getProperty("user.home"), ".sdkman", "candidates", "java")
        if (Files.isDirectory(sdkman)) {
            Files.list(sdkman).use { stream ->
                stream.filter { Files.isDirectory(it) }.forEach {
                    candidates.add(it.resolve("bin/java"))
                }
            }
        }

        // 4. macOS /Library/Java/JavaVirtualMachines
        val macJvms = Path.of("/Library/Java/JavaVirtualMachines")
        if (Files.isDirectory(macJvms)) {
            Files.list(macJvms).use { stream ->
                stream.filter { Files.isDirectory(it) }.forEach {
                    candidates.add(it.resolve("Contents/Home/bin/java"))
                }
            }
        }

        // Also ~/Library/Java/JavaVirtualMachines
        val userMacJvms = Path.of(System.getProperty("user.home"), "Library/Java/JavaVirtualMachines")
        if (Files.isDirectory(userMacJvms)) {
            Files.list(userMacJvms).use { stream ->
                stream.filter { Files.isDirectory(it) }.forEach {
                    candidates.add(it.resolve("Contents/Home/bin/java"))
                }
            }
        }

        // 5. Linux /usr/lib/jvm
        val linuxJvms = Path.of("/usr/lib/jvm")
        if (Files.isDirectory(linuxJvms)) {
            Files.list(linuxJvms).use { stream ->
                stream.filter { Files.isDirectory(it) }.forEach {
                    candidates.add(it.resolve("bin/java"))
                }
            }
        }

        return candidates
            .filter { Files.isExecutable(it) }
            .firstOrNull { javaVersion(it) >= JEFFREY_MIN_JAVA }
            ?.toString()
    }

    /** Returns the major version of the given java binary, or 0 on failure. */
    private fun javaVersion(java: Path): Int {
        return try {
            val proc = ProcessBuilder(java.toString(), "-version")
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor(5, TimeUnit.SECONDS)
            // "java version "25.0.3"" or "openjdk version "25" ..."
            val match = Regex("version \"(\\d+)").find(output)
            match?.groupValues?.get(1)?.toIntOrNull() ?: 0
        } catch (_: Exception) {
            0
        }
    }

    private fun findFreePort(): Int {
        ServerSocket(0).use { return it.localPort }
    }

    private fun pluginDataDir(): Path {
        val home = System.getProperty("user.home") ?: "."
        return Path.of(home, ".intellij-jfr-profiler")
    }
}
