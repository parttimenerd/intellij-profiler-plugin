package me.bechberger.jfrplugin.mcp

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CopilotMcpJsonRegistrarTest {

    private val defaultContent = """{
    "servers": {
        // add your MCP servers configuration here.
    }
}"""

    @Test
    fun `insert into empty servers block`() {
        val result = CopilotMcpJsonRegistrar.upsertServer(defaultContent, 63342)
        assertTrue(result.contains(""""intellij-java-profiler": {"url": "http://localhost:63342/sse"}"""))
        assertTrue(result.contains("// add your MCP servers"))
    }

    @Test
    fun `upsert updates port`() {
        val inserted = CopilotMcpJsonRegistrar.upsertServer(defaultContent, 63342)
        val updated = CopilotMcpJsonRegistrar.upsertServer(inserted, 63343)
        assertFalse(updated.contains("63342"))
        assertTrue(updated.contains("63343"))
        // only one entry
        assertEquals(1, Regex(""""intellij-java-profiler"""").findAll(updated).count())
    }

    @Test
    fun `remove cleans up entry`() {
        val inserted = CopilotMcpJsonRegistrar.upsertServer(defaultContent, 63342)
        val removed = CopilotMcpJsonRegistrar.removeServer(inserted)
        assertFalse(removed.contains("intellij-java-profiler"))
        assertTrue(removed.contains("servers"))
    }

    @Test
    fun `insert preserves existing user server`() {
        val withUser = """{
    "servers": {
        "my-server": {"url": "http://localhost:9000/sse"}
    }
}"""
        val result = CopilotMcpJsonRegistrar.upsertServer(withUser, 63342)
        assertTrue(result.contains("my-server"))
        assertTrue(result.contains("intellij-java-profiler"))
    }

    @Test
    fun `remove preserves other servers`() {
        val withBoth = CopilotMcpJsonRegistrar.upsertServer("""{
    "servers": {
        "my-server": {"url": "http://localhost:9000/sse"}
    }
}""", 63342)
        val removed = CopilotMcpJsonRegistrar.removeServer(withBoth)
        assertTrue(removed.contains("my-server"))
        assertFalse(removed.contains("intellij-java-profiler"))
    }
}
