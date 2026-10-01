// Compile-time stubs for the GitHub Copilot plugin's public MCP API.
// These interfaces are identical to com.github.copilot:core's public API.
// At runtime, the real classes from the Copilot plugin take precedence.
// These stubs are NOT shipped in the plugin JAR — they exist only so
// CopilotMcpServerProvider.kt compiles without the Copilot plugin installed.
package com.github.copilot.api.mcp

interface McpServerProvider {
    suspend fun getConfigs(): ExtInstalledMcpServerConfiguration
}

interface ExtInstalledMcpServerConfiguration {
    val configurationItems: List<ExtInstalledMcpServerConfigurationItem>
}

interface ExtInstalledMcpServerConfigurationItem {
    val mcpServerName: String
    val description: String
    val configString: String
}
