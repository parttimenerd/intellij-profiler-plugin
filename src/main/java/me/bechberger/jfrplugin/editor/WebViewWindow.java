package me.bechberger.jfrplugin.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefBrowserBuilder;
import me.bechberger.jfrplugin.util.PsiUtils;
import me.bechberger.jfrtofp.processor.Config;
import me.bechberger.jfrtofp.server.Server;

import javax.swing.*;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Window that loads an HTML5-based view.
 *
 * Must be implemented in Java — IntelliJ JCEF integration does not work correctly from Kotlin.
 */
public class WebViewWindow implements Disposable {

    private static final Logger logger = Logger.getLogger("Java JFR Profiler WebViewWindow");

    private final JBCefBrowser browser;
    private final String firefoxProfilerUrl;

    public WebViewWindow(Project project, Config config, Path file) {
        this(project, config, file, null);
    }

    public WebViewWindow(Project project, Config config, Path file, String overrideUrl) {
        this.firefoxProfilerUrl = Server.startIfNeededAndGetUrl(file, config,
                (classLocation) -> PsiUtils.INSTANCE.getFileContent(project,
                classLocation.klass, classLocation.pkg), (dest) -> PsiUtils.INSTANCE.navigateToClass(project,
                dest.klass, dest.pkg, dest.line, dest.method));

        String initialUrl = overrideUrl != null ? overrideUrl : firefoxProfilerUrl;
        logger.info("URL: " + initialUrl);
        browser = new JBCefBrowserBuilder().setEnableOpenDevToolsMenuItem(true).setUrl(initialUrl).build();
        Disposer.register(project, browser);
        // Retry until the browser actually loads the URL (CEF can drop the first load attempt)
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                if (browser.getCefBrowser().getURL().startsWith("http://localhost:")) {
                    break;
                }
                browser.getCefBrowser().loadURL(initialUrl);
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

    public void reloadWithFirefoxProfiler() {
        browser.getCefBrowser().loadURL(firefoxProfilerUrl);
    }

    @Override
    public void dispose() {
        Disposer.dispose(browser);
    }
}
