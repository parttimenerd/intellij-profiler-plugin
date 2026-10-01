package me.bechberger.jfrplugin.mcp

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import java.nio.file.Path
import kotlin.io.path.*

private val LOG = logger<CopilotMcpJsonRegistrar>()

private const val SERVER_NAME = "intellij-java-profiler"

/**
 * Writes/removes our SSE entry in Copilot's mcp.json on IDE startup/shutdown.
 *
 * Copilot's McpServerProvider extension point (1.16+) rejects providers from
 * non-Copilot plugin IDs, so we write directly to the config file that Copilot
 * watches at ~/.config/github-copilot/intellij/mcp.json.
 */
class CopilotMcpJsonRegistrar : StartupActivity.DumbAware {
    override fun runActivity(project: Project) {
        register()
    }

    companion object {
        @Volatile private var registered = false

        fun register() {
            if (registered) return
            synchronized(this) {
                if (registered) return
                registered = true
            }

            val port = getMcpPort()
            if (port != null) {
                writeEntry(port)
                installShutdownHook()
            } else {
                // MCP server not running yet — poll for up to 15s
                Thread {
                    repeat(15) {
                        Thread.sleep(1000)
                        val p = getMcpPort()
                        if (p != null) {
                            writeEntry(p)
                            installShutdownHook()
                            return@Thread
                        }
                    }
                    LOG.info("MCP server not available after 15s, skipping Copilot mcp.json registration")
                }.also { it.isDaemon = true }.start()
            }
        }

        private fun getMcpPort(): Int? = try {
            val serviceClass = Class.forName("com.intellij.mcpserver.impl.McpServerService")
            val companion = serviceClass.getField("Companion").get(null)
            val getInstance = companion.javaClass.getMethod("getInstance")
            val service = getInstance.invoke(companion)
            val isRunning = service.javaClass.getMethod("isRunning").invoke(service) as Boolean
            if (isRunning) service.javaClass.getMethod("getPort").invoke(service) as Int else null
        } catch (_: Exception) {
            null
        }

        private fun installShutdownHook() {
            ApplicationManager.getApplication().invokeLater {
                ApplicationManager.getApplication().addApplicationListener(
                    object : com.intellij.openapi.application.ApplicationListener {
                        override fun applicationExiting() = removeEntry()
                    },
                    ApplicationManager.getApplication() as Disposable
                )
            }
        }

        private fun mcpJsonPath(): Path {
            val os = System.getProperty("os.name", "").lowercase()
            val base = when {
                os.contains("win") -> Path(System.getenv("APPDATA") ?: System.getProperty("user.home"))
                else -> Path(System.getProperty("user.home"), ".config")
            }
            return base.resolve("github-copilot/intellij/mcp.json")
        }

        fun writeEntry(port: Int) {
            val path = mcpJsonPath()
            try {
                path.parent.createDirectories()
                val content = if (path.exists()) path.readText() else defaultConfig()
                val updated = upsertServer(content, port)
                if (updated != content) {
                    path.writeText(updated)
                    LOG.info("Registered $SERVER_NAME (port $port) in $path")
                } else {
                    LOG.info("$SERVER_NAME already up-to-date in $path")
                }
            } catch (e: Exception) {
                LOG.warn("Failed to write Copilot mcp.json entry", e)
            }
        }

        fun removeEntry() {
            val path = mcpJsonPath()
            try {
                if (!path.exists()) return
                val content = path.readText()
                val updated = removeServer(content)
                if (updated != content) {
                    path.writeText(updated)
                    LOG.info("Removed $SERVER_NAME from $path")
                }
            } catch (e: Exception) {
                LOG.warn("Failed to remove Copilot mcp.json entry", e)
            }
        }

        /** Insert or replace our server block inside the "servers": { ... } object. */
        internal fun upsertServer(content: String, port: Int): String {
            val entry = serverEntry(port)

            // If our block already exists, replace it (port may have changed)
            val existingPattern = Regex("""(?m)(\s*)"$SERVER_NAME"\s*:\s*\{[^}]*\}\s*,?\s*\n?""")
            if (existingPattern.containsMatchIn(content)) {
                return existingPattern.replace(content) { mr ->
                    "${mr.groupValues[1]}$entry,\n"
                }
            }

            // Otherwise inject before the closing } of "servers"
            val serversBlockClose = findServersClosingBrace(content) ?: return content
            return content.substring(0, serversBlockClose) +
                "        $entry,\n    " +
                content.substring(serversBlockClose)
        }

        /** Remove our server block from the servers object. */
        internal fun removeServer(content: String): String =
            Regex("""(?m)\s*"$SERVER_NAME"\s*:\s*\{[^}]*\}\s*,?\s*\n?""")
                .replace(content, "\n")
                .replace(Regex("\n{3,}"), "\n\n")

        private fun serverEntry(port: Int): String =
            """"$SERVER_NAME": {"url": "http://localhost:$port/sse"}"""

        private fun defaultConfig(): String = """{
    "servers": {
    }
}"""

        private fun findServersClosingBrace(content: String): Int? {
            val serversKeyIdx = content.indexOf("\"servers\"")
            if (serversKeyIdx < 0) return null
            val openBrace = content.indexOf('{', serversKeyIdx + 9)
            if (openBrace < 0) return null

            var depth = 0
            var inString = false
            var inLineComment = false
            var i = openBrace
            while (i < content.length) {
                val c = content[i]
                when {
                    inLineComment -> if (c == '\n') inLineComment = false
                    !inString && c == '/' && i + 1 < content.length && content[i + 1] == '/' -> inLineComment = true
                    c == '"' && !inLineComment -> {
                        if (inString) {
                            var backslashes = 0
                            var j = i - 1
                            while (j >= 0 && content[j] == '\\') { backslashes++; j-- }
                            if (backslashes % 2 == 0) inString = false
                        } else {
                            inString = true
                        }
                    }
                    c == '{' && !inString -> depth++
                    c == '}' && !inString -> {
                        depth--
                        if (depth == 0) return i
                    }
                }
                i++
            }
            return null
        }
    }
}
