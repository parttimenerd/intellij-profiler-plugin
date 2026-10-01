package me.bechberger.jfrplugin.mcp

import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import me.bechberger.jfrplugin.attach.AttachService
import me.bechberger.jfrplugin.attach.Engine
import me.bechberger.jfrplugin.attach.RecordingState
import me.bechberger.jfrplugin.util.PsiUtils
import kotlinx.serialization.json.*
import java.nio.file.Path

@Suppress("FunctionName")
class FirefoxProfilerMcpToolset : McpToolset {

    @McpTool
    @McpDescription(description = "Open a JFR recording in the embedded Firefox Profiler.")
    suspend fun profiler_open_jfr(
        @McpDescription(description = "Absolute filesystem path to the JFR recording file.")
        path: String,
        @McpDescription(description = "Path to the project directory. Optional if only one project is open.")
        projectPath: String? = null
    ): String = runCatching {
        val project = McpBridge.resolveProject(projectPath)
            ?: return "Error: no open project found."
        McpBridge.openJfr(project, path)
        "Opened $path"
    }.getOrElse { "Error: ${it.message}" }

    @McpTool
    @McpDescription(description = "Start a JFR recording for a running JVM. Attaches to the specified PID, or to the most recently started JVM if no PID is given.")
    suspend fun profiler_start_recording(
        @McpDescription(description = "PID of the JVM to profile. Omit to use the last known JVM.")
        pid: String? = null,
        @McpDescription(description = "Path to the project directory. Optional if only one project is open.")
        projectPath: String? = null
    ): String = runCatching {
        val project = McpBridge.resolveProject(projectPath)
            ?: return "Error: no open project found."
        val attachService = McpBridge.attachService(project)
        val targetPid = pid ?: attachService.listJvms().lastOrNull()?.pid
            ?: return "Error: no PID given and no running JVMs found."
        attachService.start(targetPid, Engine.JFR)
        "Recording started for PID $targetPid"
    }.getOrElse { "Error: ${it.message}" }

    @McpTool
    @McpDescription(description = "Stop the current JFR recording and open the result in the Firefox Profiler. Returns the path to the saved JFR file.")
    suspend fun profiler_stop_recording(
        @McpDescription(description = "PID of the JVM whose recording to stop.")
        pid: String,
        @McpDescription(description = "Path to the project directory. Optional if only one project is open.")
        projectPath: String? = null
    ): String = runCatching {
        val project = McpBridge.resolveProject(projectPath)
            ?: return "Error: no open project found."
        val attachService = McpBridge.attachService(project)
        val outputFile = attachService.stop(pid)
        McpBridge.openJfr(project, outputFile.toString())
        outputFile.toString()
    }.getOrElse { "Error: ${it.message}" }

    @McpTool
    @McpDescription(description = "Navigate the embedded Firefox Profiler to a specific view. Supported: flame_graph, call_tree, stack_chart, marker_chart, marker_table, network_chart.")
    suspend fun profiler_navigate(
        @McpDescription(description = "View name: flame_graph | call_tree | stack_chart | marker_chart | marker_table | network_chart")
        view: String,
        @McpDescription(description = "Path to the project directory. Optional if only one project is open.")
        projectPath: String? = null
    ): String = runCatching {
        val validViews = setOf("flame_graph", "call_tree", "stack_chart", "marker_chart", "marker_table", "network_chart")
        if (view !in validViews) return "Error: unknown view '$view'. Valid: ${validViews.joinToString()}"
        val project = McpBridge.resolveProject(projectPath)
            ?: return "Error: no open project found."
        val currentUrl = McpBridge.currentUrl(project)
            ?: return "Error: no JFR file is currently open in the editor."
        McpBridge.loadUrl(project, buildViewUrl(currentUrl, view))
        "Navigated to $view"
    }.getOrElse { "Error: ${it.message}" }

    @McpTool
    @McpDescription(description = "Return the current profiler state: open file, active recordings, server status, Jeffrey availability.")
    suspend fun profiler_get_status(
        @McpDescription(description = "Path to the project directory. Optional if only one project is open.")
        projectPath: String? = null
    ): String = runCatching {
        val project = McpBridge.resolveProject(projectPath)
            ?: return buildJsonObject { put("error", "no open project found") }.toString()
        val attachService = McpBridge.attachService(project)
        val recordingPids = attachService.listJvms()
            .filter { attachService.state(it.pid) is RecordingState.Recording }
            .map { it.pid }
        buildJsonObject {
            put("jfrtofpServerRunning", McpBridge.isServerRunning(project))
            put("currentFile", McpBridge.currentFilePath(project))
            put("currentUrl", McpBridge.currentUrl(project))
            put("recordingInProgress", recordingPids.isNotEmpty())
            put("recordingPids", buildJsonArray { recordingPids.forEach { add(it) } })
            put("jeffreyAvailable", McpBridge.isJeffreyAvailable())
        }.toString()
    }.getOrElse { "Error: ${it.message}" }

    @McpTool
    @McpDescription(description = "Return the top N hottest functions from the currently open JFR profile. Each entry: function, file, line, samples, percent.")
    suspend fun profiler_get_hot_functions(
        @McpDescription(description = "Number of functions to return. Default: 20.")
        n: Int = 20,
        @McpDescription(description = "Path to the project directory. Optional if only one project is open.")
        projectPath: String? = null
    ): String = runCatching<String> {
        val project = McpBridge.resolveProject(projectPath)
            ?: return "Error: no open project found."
        val filePath = McpBridge.currentFilePath(project)
            ?: return "Error: no JFR file is currently open in the editor."
        HotFunctionExtractor.extract(Path.of(filePath), n)
    }.getOrElse { "Error: ${it.message}" }

    @McpTool
    @McpDescription(description = "Navigate the IDE editor to the source of a function. Accepts fully-qualified class name, optionally with method name separated by '#'.")
    suspend fun profiler_open_function(
        @McpDescription(description = "Fully-qualified class name, e.g. com.example.Foo or com.example.Foo#processRequest")
        fqn: String,
        @McpDescription(description = "Path to the project directory. Optional if only one project is open.")
        projectPath: String? = null
    ): String = runCatching {
        val project = McpBridge.resolveProject(projectPath)
            ?: return "Error: no open project found."
        val (className, methodName) = if ('#' in fqn) {
            val idx = fqn.lastIndexOf('#')
            fqn.substring(0, idx) to fqn.substring(idx + 1)
        } else {
            fqn to ""
        }
        val lastDot = className.lastIndexOf('.')
        val pkg = if (lastDot >= 0) className.substring(0, lastDot) else ""
        val simple = if (lastDot >= 0) className.substring(lastDot + 1) else className
        if (!PsiUtils.hasClass(project, className)) return "Error: class '$className' not found in project."
        PsiUtils.navigateToClass(project, simple, pkg, 0, methodName)
        "Navigated to $fqn"
    }.getOrElse { "Error: ${it.message}" }

    private fun buildViewUrl(currentUrl: String, view: String): String {
        val viewParam = "view=$view"
        return when {
            "view=" in currentUrl -> currentUrl.replace(Regex("view=[^&]+"), viewParam)
            '?' in currentUrl -> "$currentUrl&$viewParam"
            else -> "$currentUrl?$viewParam"
        }
    }
}
