package me.bechberger.jfrplugin.mcp

import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import me.bechberger.jfrtofp.server.Server
import me.bechberger.jfrplugin.attach.AttachService
import me.bechberger.jfrplugin.config.profilerConfig
import me.bechberger.jfrplugin.editor.JFRFileEditor
import me.bechberger.jfrplugin.runner.jfr.JFRExecutor
import me.bechberger.jfrplugin.runner.ap.APExecutor
import me.bechberger.jfrplugin.viewer.JeffreyLauncher
import java.nio.file.Path

object McpBridge {

    /** Returns a project for the given path, or the first open project, or null. */
    fun resolveProject(projectPath: String?): Project? {
        val open = ProjectManager.getInstance().openProjects
        if (projectPath != null) {
            val p = Path.of(projectPath)
            return open.firstOrNull { project ->
                project.basePath?.let { Path.of(it).startsWith(p) || p.startsWith(Path.of(it)) } == true
            } ?: open.firstOrNull()
        }
        return open.firstOrNull()
    }

    fun activeJfrEditor(project: Project): JFRFileEditor? =
        FileEditorManager.getInstance(project).selectedEditor as? JFRFileEditor

    fun currentUrl(project: Project): String? =
        activeJfrEditor(project)?.webViewWindow?.currentUrl

    fun currentFilePath(project: Project): String? =
        activeJfrEditor(project)?.file?.path

    /** Opens a JFR file in the Firefox Profiler. Returns the URL on success or throws. */
    fun openJfr(project: Project, path: String): String {
        val file = Path.of(path)
        val config = project.profilerConfig.conversionConfig.toConfig()
        val url = Server.startIfNeededAndGetUrl(file, config, null, null)
        val vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file)
            ?: error("File not found: $path")
        ApplicationManager.getApplication().invokeLater {
            FileEditorManager.getInstance(project).openFile(vf, true)
        }
        Thread.sleep(500)
        activeJfrEditor(project)?.webViewWindow?.loadUrl(url)
        return url
    }

    fun loadUrl(project: Project, url: String) {
        val editor = activeJfrEditor(project) ?: error("No JFR file is currently open in the editor.")
        editor.webViewWindow.loadUrl(url)
    }

    fun attachService(project: Project): AttachService = AttachService.getInstance(project)

    fun isJeffreyAvailable(): Boolean = JeffreyLauncher.isAvailable()

    fun isServerRunning(project: Project): Boolean = currentUrl(project) != null

    /**
     * Thread-local override for async-profiler event type, set during [runWithProfiling].
     * Read by APPluginRunConfigurationExtension to allow MCP-driven event selection.
     */
    val apEventOverride: ThreadLocal<String?> = ThreadLocal.withInitial { null }

    /**
     * Lists all run configurations in the project.
     * Returns pairs of (name, type display name).
     */
    fun listRunConfigurations(project: Project): List<Pair<String, String>> =
        RunManager.getInstance(project).allConfigurationsList
            .map { it.name to it.type.displayName }

    /**
     * Executes a run configuration with profiling.
     * [engine]: "jfr", "ap" (async-profiler), or "ap:cpu" / "ap:wall" / "ap:alloc" etc.
     * [configName]: run configuration name, or null to use the selected one.
     */
    fun runWithProfiling(project: Project, configName: String?, engine: String) {
        val runManager = RunManager.getInstance(project)
        val settings = if (configName != null) {
            runManager.allSettings.firstOrNull { it.name.equals(configName, ignoreCase = true) }
                ?: error("Run configuration '$configName' not found.")
        } else {
            runManager.selectedConfiguration
                ?: error("No run configuration selected.")
        }
        // Parse engine: "jfr", "ap", "ap:cpu", "ap:wall", "ap:alloc", "ap:ctimer", etc.
        val (useAp, apEvent) = when {
            engine.lowercase() == "jfr" -> false to null
            engine.lowercase().startsWith("ap") -> {
                val event = engine.substringAfter(":", "").takeIf { it.isNotBlank() }
                true to event
            }
            else -> false to null
        }
        val executorId = if (useAp) APExecutor.EXECUTOR_ID else JFRExecutor.EXECUTOR_ID
        val executor = ExecutorRegistry.getInstance().getExecutorById(executorId)
            ?: error("Executor '$executorId' not found.")
        apEventOverride.set(apEvent)
        ApplicationManager.getApplication().invokeLater {
            try {
                ProgramRunnerUtil.executeConfiguration(settings, executor)
            } finally {
                apEventOverride.remove()
            }
        }
    }

    /**
     * Finds the `jfr` CLI binary. Search order:
     *   1. Project SDK (most likely to match the JFR file format)
     *   2. JAVA_HOME env var
     *   3. `jfr` on PATH
     * Returns null if not found.
     */
    fun findJfrCli(project: Project?): Path? {
        val candidates = mutableListOf<Path>()
        if (project != null) {
            ProjectRootManager.getInstance(project).projectSdk?.homePath
                ?.let { candidates.add(Path.of(it, "bin", "jfr")) }
        }
        System.getenv("JAVA_HOME")?.let { candidates.add(Path.of(it, "bin", "jfr")) }
        runCatching {
            ProcessBuilder("which", "jfr").start().inputStream.bufferedReader().readLine()
        }.getOrNull()?.let { candidates.add(Path.of(it)) }
        return candidates.firstOrNull { it.toFile().canExecute() }
    }

    /**
     * Runs `jfr <args>` and returns stdout (truncated to [maxChars]).
     * Throws if the binary is not found or the command fails.
     */
    fun runJfrCli(project: Project?, vararg args: String, maxChars: Int = 8000): String {
        val jfr = findJfrCli(project) ?: error("jfr CLI not found. Ensure JDK 9+ is on PATH or set JAVA_HOME.")
        val proc = ProcessBuilder(listOf(jfr.toString()) + args.toList())
            .redirectErrorStream(true)
            .start()
        val output = proc.inputStream.bufferedReader().readText()
        val exit = proc.waitFor()
        val result = if (output.length > maxChars) output.take(maxChars) + "\n[truncated]" else output
        if (exit != 0) error("jfr exited with code $exit:\n$result")
        return result
    }
}
