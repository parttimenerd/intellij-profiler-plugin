package me.bechberger.jfrplugin.mcp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.LocalFileSystem
import me.bechberger.jfrtofp.server.Server
import me.bechberger.jfrplugin.attach.AttachService
import me.bechberger.jfrplugin.config.profilerConfig
import me.bechberger.jfrplugin.editor.JFRFileEditor
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

    fun isJeffreyAvailable(): Boolean = JeffreyLauncher.isJdkAvailable()

    fun isServerRunning(project: Project): Boolean = currentUrl(project) != null
}
