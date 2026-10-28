package me.bechberger.jfrplugin.editor

import com.intellij.diff.util.FileEditorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import me.bechberger.jfrplugin.viewer.JeffreyLauncher
import java.awt.BorderLayout
import java.awt.event.HierarchyEvent
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class JeffreyFileEditor(private val project: Project, private val virtualFile: VirtualFile) : FileEditorBase() {

    private val wrapper = JPanel(BorderLayout())
    private var browserWindow: JeffreyBrowserWindow? = null
    private val launched = AtomicBoolean(false)

    init {
        wrapper.add(JLabel("Select the Jeffrey tab to load it.", JLabel.CENTER), BorderLayout.CENTER)
        wrapper.addHierarchyListener { e ->
            if (e.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L && wrapper.isShowing) {
                startJeffrey()
            }
        }
    }

    private fun startJeffrey() {
        if (!launched.compareAndSet(false, true)) return
        wrapper.removeAll()
        wrapper.add(JLabel("Starting Jeffrey…", JLabel.CENTER), BorderLayout.CENTER)
        wrapper.revalidate()
        wrapper.repaint()
        Thread {
            val url = JeffreyLauncher.startOrReuseAndGetUrl(virtualFile.toNioPath(), project)
            javax.swing.SwingUtilities.invokeLater {
                wrapper.removeAll()
                if (url != null) {
                    val browser = JeffreyBrowserWindow(project, url)
                    browserWindow = browser
                    wrapper.add(browser.component, BorderLayout.CENTER)
                } else {
                    wrapper.add(
                        JLabel("Jeffrey unavailable — no JDK ${JeffreyLauncher.JEFFREY_MIN_JAVA}+ found. Check IDE log.", JLabel.CENTER),
                        BorderLayout.CENTER
                    )
                }
                wrapper.revalidate()
                wrapper.repaint()
            }
        }.also { it.isDaemon = true }.start()
    }

    override fun getName(): String = NAME
    override fun getFile(): VirtualFile = virtualFile
    override fun getComponent(): JComponent = wrapper
    override fun getPreferredFocusedComponent(): JComponent = wrapper

    override fun dispose() {
        browserWindow?.dispose()
    }

    companion object {
        const val NAME = "Jeffrey"
        private val logger = Logger.getLogger("JeffreyFileEditor")
    }
}
