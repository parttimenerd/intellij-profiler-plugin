package me.bechberger.jfrplugin

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory

/**
 * Verifies the plugin distribution bundle includes all transitive runtime dependencies.
 *
 * Regression test for https://github.com/parttimenerd/intellij-profiler-plugin/issues/38:
 * jfrtofp-server's Javalin/Jetty transitive deps were absent from the production plugin lib,
 * causing NoClassDefFoundError at runtime when opening a JFR file.
 */
class PluginDistributionTest {

    /**
     * Classes that must be loadable from the plugin's bundled JARs.
     * These are the external runtime dependencies of jfrtofp-server that the plugin
     * must bundle — they are NOT provided by the IntelliJ platform.
     */
    private val requiredClasses = listOf(
        // Javalin web framework (used by jfrtofp-server's embedded HTTP server)
        "io.javalin.Javalin",
        "io.javalin.util.JavalinException",
        "io.javalin.http.Context",
        "io.javalin.http.Handler",
        // Jetty (Javalin's embedded HTTP server — Jetty 12 uses jakarta.ee10.servlet namespace)
        "org.eclipse.jetty.server.Server",
        "org.eclipse.jetty.ee10.servlet.ServletContextHandler",
        // Jackson (used by jfrtofp-server for JSON)
        "com.fasterxml.jackson.databind.ObjectMapper",
    )

    @Test
    fun `plugin lib contains all transitive runtime dependencies`() {
        val libDir = findPluginLibDir()
        val jars = Files.list(libDir)
            .filter { it.extension == "jar" }
            .map { it.toUri().toURL() }
            .toList()

        check(jars.isNotEmpty()) { "No JARs found in plugin lib dir: $libDir" }

        // Use a classloader that only sees the plugin's bundled JARs, not the test classpath,
        // so we catch exactly what users will have at runtime.
        val loader = URLClassLoader(jars.toTypedArray(), ClassLoader.getPlatformClassLoader())

        val missing = requiredClasses.filter { className ->
            try {
                loader.loadClass(className)
                false
            } catch (_: ClassNotFoundException) {
                true
            }
        }

        if (missing.isNotEmpty()) {
            val jarNames = Files.list(libDir)
                .filter { it.extension == "jar" }
                .map { it.fileName.toString() }
                .sorted()
                .toList()
                .joinToString("\n  - ")
            fail(
                "Plugin distribution is missing required runtime classes.\n" +
                "Missing classes:\n${missing.joinToString("\n") { "  - $it" }}\n\n" +
                "Bundled JARs in $libDir:\n  - $jarNames\n\n" +
                "Fix: ensure jfrtofp-server's transitive dependencies (Javalin, Jetty, Jackson) " +
                "are included in the plugin's runtimeClasspath and not excluded by the build."
            )
        }
    }

    private fun findPluginLibDir(): Path {
        val projectDir = Path.of(System.getProperty("user.dir"))

        // The IntelliJ Platform Gradle plugin's prepareSandbox task places plugin libs here.
        // Walk build/idea-sandbox to find the plugin lib dir regardless of platform version suffix.
        val sandboxBase = projectDir.resolve("build/idea-sandbox")
        if (sandboxBase.isDirectory()) {
            val libDir = Files.walk(sandboxBase)
                .filter { it.isDirectory() && it.fileName.toString() == "lib" }
                .filter { it.parent?.fileName?.toString() == "Java JFR Profiler" }
                // Prefer plugins/ over plugins-test/ (plugins-test has extra test deps)
                .filter { !it.toString().contains("plugins-test") }
                .findFirst()
            if (libDir.isPresent) return libDir.get()
        }

        // Fallback: unpack the distribution ZIP if sandbox not available
        val distDir = projectDir.resolve("build/distributions")
        if (distDir.isDirectory()) {
            val zip = Files.list(distDir).filter { it.extension == "zip" }.findFirst()
            if (zip.isPresent) {
                val unpackDir = distDir.resolve("content/Java JFR Profiler/lib")
                if (unpackDir.isDirectory()) return unpackDir
            }
        }

        error(
            "Could not find plugin lib directory. Run './gradlew prepareSandbox' or " +
            "'./gradlew buildPlugin' first, then re-run the test."
        )
    }
}
