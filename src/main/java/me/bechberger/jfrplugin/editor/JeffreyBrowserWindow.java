package me.bechberger.jfrplugin.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefBrowserBuilder;

import javax.swing.*;
import java.util.logging.Logger;

/**
 * A JCEF browser that shows the Jeffrey profiler UI.
 * Must be implemented in Java — IntelliJ JCEF integration does not work correctly from Kotlin.
 */
public class JeffreyBrowserWindow implements Disposable {

    private static final Logger logger = Logger.getLogger("JeffreyBrowserWindow");

    private final JBCefBrowser browser;

    public JeffreyBrowserWindow(Project project, String url) {
        logger.info("Opening Jeffrey at: " + url);
        browser = new JBCefBrowserBuilder()
                .setEnableOpenDevToolsMenuItem(true)
                .setUrl(url)
                .build();
        Disposer.register(project, browser);

        // Retry until the browser actually loads (CEF can drop the first load attempt)
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                String current = browser.getCefBrowser().getURL();
                if (current != null && current.startsWith("http://localhost:")) break;
                browser.getCefBrowser().loadURL(url);
            }
        }).start();
    }

    public JComponent getComponent() {
        return browser.getComponent();
    }

    @Override
    public void dispose() {
        Disposer.dispose(browser);
    }
}
