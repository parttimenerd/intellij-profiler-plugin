package me.bechberger.jfrplugin.mcp

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.project.Project
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import kotlin.io.path.*

private val LOG = logger<CopilotMcpJsonRegistrar>()

private const val SERVER_NAME = "intellij-java-profiler"

/**
 * Writes/removes our SSE entry in Copilot's mcp.json on IDE startup/shutdown.
 *
 * The McpServerProvider extension point in Copilot 1.16 only accepts providers
 * from com.github.copilot and com.github.copilot.appmod plugin IDs, so we
 * write directly to the config file that Copilot watches.
 */
class CopilotMcpJsonRegistrar : ProjectActivity {
    override suspend fun execute(project: Project) {
        // Only register once across all open projects
        if (!project.isDefault && ApplicationManager.getApplication().isUnitTestMode.not()) {
            register()
        }
    }

    companion object {
        private var registered = false

        fun register() {
            if (registered) return
            registered = true

            val service = try {
                McpServerService.Companion.getInstance()
            } catch (_: Exception) {
                return
            }

            if (!service.isRunning) {
                // Wait briefly for the MCP server to start, then register
                Thread {
                    repeat(10) {
                        Thread.sleep(1000)
                        if (service.isRunning) {
                            writeEntry(service.port)
                            installShutdownHook()
                            return@Thread
                        }
                    }
                }.also { it.isDaemon = true }.start()
            } else {
                writeEntry(service.port)
                installShutdownHook()
            }
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

        private fun mcpJsonPath(): Path? {
            val os = System.getProperty("os.name", "").lowercase()
            val base = when {
                os.contains("mac") -> Path(System.getProperty("user.home"), ".config")
                os.contains("win") -> Path(System.getenv("APPDATA") ?: return null)
                else -> Path(System.getProperty("user.home"), ".config")
            }
            return base.resolve("github-copilot/intellij/mcp.json")
        }

        fun writeEntry(port: Int) {
            val path = mcpJsonPath() ?: return
            try {
                path.parent.createDirectories()
                val content = if (path.exists()) path.readText() else defaultConfig()
                val updated = upsertServer(content, port)
                if (updated != content) {
                    path.writeText(updated)
                    LOG.info("Registered $SERVER_NAME in ${path}")
                }
            } catch (e: Exception) {
                LOG.warn("Failed to write Copilot mcp.json entry", e)
            }
        }

        fun removeEntry() {
            val path = mcpJsonPath() ?: return
            try {
                if (!path.exists()) return
                val content = path.readText()
                val updated = removeServer(content)
                if (updated != content) {
                    path.writeText(updated)
                    LOG.info("Removed $SERVER_NAME from ${path}")
                }
            } catch (e: Exception) {
                LOG.warn("Failed to remove Copilot mcp.json entry", e)
            }
        }

        /** Insert or replace our server block inside the "servers": { ... } object. */
        internal fun upsertServer(content: String, port: Int): String {
            val entry = serverEntry(port)

            // If our block already exists, replace it (port may have changed)
            val existingPattern = Regex(
                """(?m)(\s*)"$SERVER_NAME"\s*:\s*\{[^}]*\}\s*,?\s*\n?"""
            )
            if (existingPattern.containsMatchIn(content)) {
                return existingPattern.replace(content) { mr ->
                    val indent = mr.groupValues[1]
                    "$indent$entry,\n"
                }
            }

            // Otherwise inject before the closing } of "servers"
            // Find the last } that closes the servers block
            val serversBlockClose = findServersClosingBrace(content) ?: return content
            val indent = "        " // 8 spaces, matching Copilot's default style
            return content.substring(0, serversBlockClose) +
                "$indent$entry,\n    " +
                content.substring(serversBlockClose)
        }

        /** Remove our server block from the servers object. */
        internal fun removeServer(content: String): String {
            val pattern = Regex(
                """(?m)\s*"$SERVER_NAME"\s*:\s*\{[^}]*\}\s*,?\s*\n?"""
            )
            return pattern.replace(content, "\n")
                .replace(Regex("\n{3,}"), "\n\n") // collapse extra blank lines
        }

        private fun serverEntry(port: Int): String =
            """"$SERVER_NAME": {"url": "http://localhost:$port/sse"}"""

        private fun defaultConfig(): String = """{
    "servers": {
    }
}"""

        private fun findServersClosingBrace(content: String): Int? {
            // Find "servers": { and then locate its matching }
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
                    // Only treat // as comment when not inside a string
                    !inString && c == '/' && i + 1 < content.length && content[i + 1] == '/' -> inLineComment = true
                    c == '"' && !inLineComment -> {
                        // handle escaped quotes
                        if (inString) {
                            // count backslashes before this quote
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
