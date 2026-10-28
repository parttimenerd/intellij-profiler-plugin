package me.bechberger.jfrplugin.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefBrowserBuilder;
import me.bechberger.jfrplugin.util.PsiUtils;
import me.bechberger.jfrtofp.processor.Config;
import me.bechberger.jfrtofp.server.Server;

import javax.swing.*;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Window that loads an HTML5-based view.
 *
 * Must be implemented in Java — IntelliJ JCEF integration does not work correctly from Kotlin.
 */
public class WebViewWindow implements Disposable {

    private static final Logger logger = Logger.getLogger("Java JFR Profiler WebViewWindow");

    private final JBCefBrowser browser;
    private volatile String firefoxProfilerUrl;

    public WebViewWindow(Project project, Config config, Path file) {
        this(project, config, file, null);
    }

    public WebViewWindow(Project project, Config config, Path file, String overrideUrl) {
        JBCefBrowserBuilder builder = new JBCefBrowserBuilder()
                .setEnableOpenDevToolsMenuItem(true);
        if (!JBCefApp.isOffScreenRenderingModeEnabled()) {
            builder.setOffScreenRendering(false);
        }
        browser = builder.build();
        Disposer.register(project, browser);

        CompletableFuture.runAsync(() -> {
            this.firefoxProfilerUrl = Server.startIfNeededAndGetUrl(file, config,
                    (classLocation) -> PsiUtils.INSTANCE.getFileContent(project,
                            classLocation.klass, classLocation.pkg),
                    (dest) -> PsiUtils.INSTANCE.navigateToClass(project,
                            dest.klass, dest.pkg, dest.line, dest.method));

            String url = overrideUrl != null ? overrideUrl : firefoxProfilerUrl;
            logger.info("URL: " + url);
            loadWithRetry(url);
        });
    }

    /** Load the URL, retrying if CEF drops the first attempt (happens before the browser is fully initialised). */
    private void loadWithRetry(String url) {
        browser.getCefBrowser().loadURL(url);
        new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                try { Thread.sleep(50); } catch (InterruptedException e) { return; }
                String current = browser.getCefBrowser().getURL();
                if (current != null && current.startsWith("http://localhost:")) return;
                browser.getCefBrowser().loadURL(url);
            }
        }).start();
    }

    public JComponent getComponent() {
        return browser.getComponent();
    }

    public void reload() {
        browser.getCefBrowser().reload();
    }

    public void loadUrl(String url) {
        browser.getCefBrowser().loadURL(url);
    }

    public String getCurrentUrl() {
        return browser.getCefBrowser().getURL();
    }

    public void reloadWithFirefoxProfiler() {
        String url = firefoxProfilerUrl;
        if (url != null) browser.getCefBrowser().loadURL(url);
    }

    @Override
    public void dispose() {
        Disposer.dispose(browser);
    }
}
