package me.bechberger.jfrplugin.mcp

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import kotlinx.serialization.json.*
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
        // Pre-initialize JBCefApp on the EDT during startup so its static <clinit>
        // doesn't fire inside a service initializer when the first JFR editor opens.
        // That path triggers a "service requested during class init" IDE error in 2025.2+.
        ApplicationManager.getApplication().invokeLater {
            try {
                com.intellij.ui.jcef.JBCefApp.getInstance()
            } catch (_: Exception) {}
        }
        register()
    }

    companion object {
        @Volatile private var registered = false

        fun register() {
            if (registered) return
            synchronized(this) {
                if (registered) return
            }

            val port = getMcpPort()
            if (port != null) {
                registered = true
                writeEntry(port)
                installShutdownHook()
            } else {
                // MCP server not running yet — poll for up to 30s
                Thread {
                    repeat(30) {
                        Thread.sleep(1000)
                        val p = getMcpPort()
                        if (p != null) {
                            registered = true
                            writeEntry(p)
                            installShutdownHook()
                            return@Thread
                        }
                    }
                    LOG.info("MCP server not available after 30s, skipping Copilot mcp.json registration")
                }.also { it.isDaemon = true }.start()
            }
        }

        private fun getMcpPort(): Int? = try {
            val serviceClass = Class.forName("com.intellij.mcpserver.impl.McpServerService")
            val companion = serviceClass.getField("Companion").get(null)
            val service = companion.javaClass.getMethod("getInstance").invoke(companion)
            val isRunning = service.javaClass.getMethod("isRunning").invoke(service) as Boolean
            if (!isRunning) {
                // start() enables the setting and starts the server
                service.javaClass.getMethod("start").invoke(service)
                LOG.info("Started JetBrains MCP server via start()")
            }
            val port = service.javaClass.getMethod("getPort").invoke(service) as Int
            if (port > 0) port else null
        } catch (e: Exception) {
            LOG.info("getMcpPort failed: ${e.javaClass.simpleName}: ${e.message}")
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

        private val json = Json {
            prettyPrint = true
            prettyPrintIndent = "    "
            ignoreUnknownKeys = true
        }

        private fun stripComments(content: String): String {
            val sb = StringBuilder(content.length)
            var inString = false
            var i = 0
            while (i < content.length) {
                val c = content[i]
                when {
                    c == '"' && !inString -> { inString = true; sb.append(c) }
                    c == '"' && inString -> {
                        // count preceding backslashes to detect escaped quote
                        var backslashes = 0
                        var j = i - 1
                        while (j >= 0 && content[j] == '\\') { backslashes++; j-- }
                        if (backslashes % 2 == 0) inString = false
                        sb.append(c)
                    }
                    !inString && c == '/' && i + 1 < content.length && content[i + 1] == '/' -> {
                        // skip to end of line
                        while (i < content.length && content[i] != '\n') i++
                        continue
                    }
                    else -> sb.append(c)
                }
                i++
            }
            return sb.toString()
        }

        /** Insert or replace our server block inside the "servers": { ... } object. */
        internal fun upsertServer(content: String, port: Int): String {
            val root = json.parseToJsonElement(stripComments(content)).jsonObject
            val servers = (root["servers"] as? JsonObject ?: JsonObject(emptyMap())).toMutableMap()
            servers[SERVER_NAME] = buildJsonObject {
                put("type", "sse")
                put("url", "http://127.0.0.1:$port/sse")
            }
            val updated = JsonObject(root.toMutableMap().also { it["servers"] = JsonObject(servers) })
            return json.encodeToString(JsonObject.serializer(), updated)
        }

        /** Remove our server block from the servers object. */
        internal fun removeServer(content: String): String {
            val root = json.parseToJsonElement(stripComments(content)).jsonObject
            val servers = (root["servers"] as? JsonObject ?: JsonObject(emptyMap())).toMutableMap()
            if (SERVER_NAME !in servers) return content
            servers.remove(SERVER_NAME)
            val updated = JsonObject(root.toMutableMap().also { it["servers"] = JsonObject(servers) })
            return json.encodeToString(JsonObject.serializer(), updated)
        }

        private fun defaultConfig(): String = """{
    "servers": {
    }
}"""
    }
}
