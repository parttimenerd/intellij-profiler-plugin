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

    /** Minimum Java major version required to run the bundled Jeffrey jar. */
    const val JEFFREY_MIN_JAVA = 25

    /**
     * Starts Jeffrey (or reuses a running instance), uploads [jfrFile] via the REST API,
     * and returns the URL of the recording page for that specific file.
     * Returns null if no suitable JDK is available or startup fails.
     */
    fun startOrReuseAndGetUrl(jfrFile: Path): String? =
        startOrReuseAndGetUrlForFiles(listOf(jfrFile))

    /**
     * Starts Jeffrey (or reuses a running instance), uploads all [jfrFiles] via the REST API,
     * and returns a URL pointing to the first recording (or the recordings list when multiple
     * files are uploaded). Returns null if no suitable JDK is available or startup fails.
     */
    fun startOrReuseAndGetUrlForFiles(jfrFiles: List<Path>): String? {
        if (jfrFiles.isEmpty()) return null
        if (!isRunning()) {
            if (!startAndGetUrl()) return null
        }
        val base = "http://localhost:$currentPort"
        val profileUrls = jfrFiles.mapNotNull { file ->
            val recordingId = uploadFile(file) ?: return@mapNotNull null
            analyzeRecording(recordingId)
        }
        return when {
            profileUrls.isEmpty() -> "$base/recordings"
            profileUrls.size == 1 -> profileUrls.first()
            else -> "$base/recordings"
        }
    }

    private fun uploadFile(jfrFile: Path): String? {
        val base = "http://localhost:$currentPort"
        return try {
            val boundary = "jeffreyboundary${System.currentTimeMillis()}"
            val fileBytes = Files.readAllBytes(jfrFile)
            val fileName = jfrFile.fileName.toString()

            val body = buildMultipartBody(boundary, fileName, fileBytes)

            val url = java.net.URI("$base/api/internal/recordings").toURL()
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 5_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.outputStream.use { it.write(body) }

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                extractJsonString(response, "recordingId").also {
                    if (it == null) LOG.warning("Jeffrey upload response missing recordingId: $response")
                }
            } else {
                LOG.warning("Jeffrey upload of ${jfrFile.fileName} returned HTTP ${conn.responseCode}")
                null
            }
        } catch (e: Exception) {
            LOG.warning("Jeffrey upload of ${jfrFile.fileName} failed: ${e.message}")
            null
        }
    }

    /**
     * Analyzes a previously uploaded recording and returns the profile URL
     * (`/profiles/<profileId>/overview`), or null on failure.
     * The analyze call is synchronous — it returns only after analysis is complete.
     *
     * Note: the analyze endpoint path is `/recordings/recordings/<id>/analyze` because
     * the RecordingsService base is already `/api/internal/recordings` and the method path
     * adds another `/recordings/<id>/analyze`.
     */
    private fun analyzeRecording(recordingId: String): String? {
        val base = "http://localhost:$currentPort"
        return try {
            val url = java.net.URI("$base/api/internal/recordings/recordings/$recordingId/analyze").toURL()
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 5_000
            conn.readTimeout = 300_000
            conn.connect()

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val profileId = extractJsonString(response, "profileId")
                if (profileId != null) "$base/profiles/$profileId/overview"
                else { LOG.warning("Jeffrey analyze missing profileId: $response"); null }
            } else {
                LOG.warning("Jeffrey analyze of $recordingId returned HTTP ${conn.responseCode}")
                null
            }
        } catch (e: Exception) {
            LOG.warning("Jeffrey analyze of $recordingId failed: ${e.message}")
            null
        }
    }

    private fun buildMultipartBody(boundary: String, fileName: String, fileBytes: ByteArray): ByteArray {
        val crlf = "\r\n"
        val header = "--$boundary$crlf" +
            "Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"$crlf" +
            "Content-Type: application/octet-stream$crlf$crlf"
        val footer = "$crlf--$boundary--$crlf"
        return header.toByteArray() + fileBytes + footer.toByteArray()
    }

    private fun extractJsonString(json: String, key: String): String? {
        val pattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"([^\"]+)\"")
        return pattern.find(json)?.groupValues?.get(1)
    }

    private fun startAndGetUrl(): Boolean {
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

        val cmd = listOf(
            javaExe,
            "-jar", jar.toAbsolutePath().toString(),
            "--server.port=$port"
        )
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
