package me.bechberger.jfrplugin.editor

import com.intellij.diff.util.FileEditorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import me.bechberger.jfrplugin.viewer.JeffreyLauncher
import javax.swing.JComponent
import javax.swing.JLabel
import java.util.logging.Logger

class JeffreyFileEditor(private val project: Project, private val virtualFile: VirtualFile) : FileEditorBase() {

    private val component: JComponent
    private var browserWindow: JeffreyBrowserWindow? = null

    init {
        // Start Jeffrey (or reuse running instance) in background; show a placeholder until ready
        val placeholder = JLabel("Starting Jeffrey…", JLabel.CENTER)
        component = placeholder

        Thread {
            val url = JeffreyLauncher.startOrReuseAndGetUrl(virtualFile.toNioPath())
            if (url != null) {
                val browser = JeffreyBrowserWindow(project, url)
                browserWindow = browser
                javax.swing.SwingUtilities.invokeLater {
                    val parent = placeholder.parent
                    if (parent != null) {
                        val idx = (parent as? java.awt.Container)?.components?.indexOf(placeholder) ?: -1
                        parent.remove(placeholder)
                        parent.add(browser.component)
                        parent.revalidate()
                        parent.repaint()
                    }
                }
            } else {
                javax.swing.SwingUtilities.invokeLater {
                    placeholder.text = "Jeffrey unavailable — no JDK ${JeffreyLauncher.JEFFREY_MIN_JAVA}+ found. Check IDE log."
                }
            }
        }.also { it.isDaemon = true }.start()
    }

    override fun getName(): String = NAME
    override fun getFile(): VirtualFile = virtualFile
    override fun getComponent(): JComponent = component
    override fun getPreferredFocusedComponent(): JComponent = component

    override fun dispose() {
        browserWindow?.dispose()
    }

    companion object {
        const val NAME = "Jeffrey"
        private val logger = Logger.getLogger("JeffreyFileEditor")
    }
}
