package me.bechberger.jfrplugin.mcp

import com.github.copilot.api.mcp.ExtInstalledMcpServerConfiguration
import com.github.copilot.api.mcp.ExtInstalledMcpServerConfigurationItem
import com.github.copilot.api.mcp.McpServerProvider
import com.intellij.mcpserver.impl.McpServerService

class CopilotMcpServerProvider : McpServerProvider {
    override suspend fun getConfigs(): ExtInstalledMcpServerConfiguration {
        val service = McpServerService.Companion.getInstance()
        if (!service.isRunning) return object : ExtInstalledMcpServerConfiguration {
            override val configurationItems = emptyList<ExtInstalledMcpServerConfigurationItem>()
        }
        val sseUrl = "http://localhost:${service.port}/sse"
        return object : ExtInstalledMcpServerConfiguration {
            override val configurationItems = listOf(
                object : ExtInstalledMcpServerConfigurationItem {
                    override val mcpServerName = "intellij-java-profiler"
                    override val description =
                        "Java JFR Profiler tools: profile runs, open JFR files, get hot functions, navigate to source"
                    override val configString = """{"url":"$sseUrl"}"""
                }
            )
        }
    }
}
