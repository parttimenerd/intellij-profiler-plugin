# MCP Toolset Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `FirefoxProfilerMcpToolset` to jfrplugin so JetBrains AI, GitHub Copilot, and Claude Code can open JFR files, start/stop profiling, navigate the Firefox Profiler UI, and inspect hot functions via MCP.

**Architecture:** The toolset contributes to IntelliJ's built-in MCP Server via the `com.intellij.mcpServer.mcpToolset` extension point in an optional `mcp.xml` loaded only when `com.intellij.mcpServer` is present. A thin `McpBridge` object provides all runtime state access (WebViewWindow, AttachService, JeffreyLauncher). The dependency is optional — the plugin works normally without `com.intellij.mcpServer`.

**Tech Stack:** Kotlin, IntelliJ Platform SDK (2025.1+), `com.intellij.mcpServer` optional plugin, `me.bechberger:jfrtofp:0.0.9`, kotlinx-serialization-json.

---

## File Structure

| File | Action | Responsibility |
|---|---|---|
| `src/main/kotlin/me/bechberger/jfrplugin/mcp/McpBridge.kt` | **Create** | Access project services and editors; all runtime state for MCP tools |
| `src/main/kotlin/me/bechberger/jfrplugin/mcp/FirefoxProfilerMcpToolset.kt` | **Create** | 7 MCP tool methods; delegates everything to McpBridge |
| `src/main/resources/META-INF/mcp.xml` | **Create** | Optional extension registration for `com.intellij.mcpServer` |
| `src/main/resources/META-INF/plugin.xml` | **Modify** | Add optional `<depends>` on `com.intellij.mcpServer` pointing to `mcp.xml` |
| `build.gradle.kts` | **Modify** | Add `optionalPlugin("com.intellij.mcpServer")` to intellijPlatform deps |
| `src/main/java/me/bechberger/jfrplugin/editor/WebViewWindow.java` | **Modify** | Add public `getCurrentUrl(): String` method |

> **Note on testing**: IntelliJ MCP tools require a running IDE — unit tests for individual bridge methods are not practical. The test strategy is a compile-verify approach: confirm the project builds with the optional dep, then smoke-test the full plugin in a sandboxed IDE (`./gradlew runIde`).

---

## Task 1: Add `getCurrentUrl()` to WebViewWindow

The spec requires `WebViewWindow.getCurrentUrl()` to get the active profiler URL for `profiler_get_status` and `profiler_navigate`. The private `browser` field (a `JBCefBrowser`) exposes `getCefBrowser().getURL()`.

**Files:**
- Modify: `src/main/java/me/bechberger/jfrplugin/editor/WebViewWindow.java`

- [ ] **Step 1: Read the current file to find the browser field and existing public methods**

  Run: `grep -n "browser\|public\|loadUrl\|getCurrentUrl" src/main/java/me/bechberger/jfrplugin/editor/WebViewWindow.java`

  Confirm the field name is `browser` (type `JBCefBrowser`) and that `loadUrl` already exists.

- [ ] **Step 2: Add `getCurrentUrl()` after the existing `loadUrl()` method**

  Find the line containing `public void loadUrl(String url)` and add the new method immediately after its closing brace:

  ```java
  public String getCurrentUrl() {
      return browser.getCefBrowser().getURL();
  }
  ```

- [ ] **Step 3: Verify the file compiles**

  Run: `./gradlew compileJava 2>&1 | tail -20`

  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

  ```bash
  git add src/main/java/me/bechberger/jfrplugin/editor/WebViewWindow.java
  git commit -m "feat: expose getCurrentUrl() on WebViewWindow for MCP bridge"
  ```

---

## Task 2: Wire optional `com.intellij.mcpServer` dependency

The plugin must declare `com.intellij.mcpServer` as an optional dependency so the MCP toolset is only registered when the plugin is present. Without this, the plugin fails to load on IDEs without MCP support.

**Background:** IntelliJ's optional plugin dependency mechanism: `<depends optional="true" config-file="mcp.xml">com.intellij.mcpServer</depends>` in `plugin.xml` causes IntelliJ to load `mcp.xml` only when `com.intellij.mcpServer` is installed. Extensions in `mcp.xml` reference classes that are only available when the dep is present — this is safe because IntelliJ skips loading `mcp.xml` entirely when the dep is absent.

**Files:**
- Modify: `src/main/resources/META-INF/plugin.xml`
- Modify: `build.gradle.kts`
- Create: `src/main/resources/META-INF/mcp.xml`

- [ ] **Step 1: Add the optional `<depends>` to plugin.xml**

  Open `src/main/resources/META-INF/plugin.xml`. Find the last existing `<depends>` line. Add immediately after it:

  ```xml
  <depends optional="true" config-file="mcp.xml">com.intellij.mcpServer</depends>
  ```

- [ ] **Step 2: Create `mcp.xml`**

  Create `src/main/resources/META-INF/mcp.xml` with:

  ```xml
  <idea-plugin>
    <extensions defaultExtensionNs="com.intellij">
      <mcpServer.mcpToolset
          implementation="me.bechberger.jfrplugin.mcp.FirefoxProfilerMcpToolset"/>
    </extensions>
  </idea-plugin>
  ```

- [ ] **Step 3: Add `optionalPlugin` to build.gradle.kts**

  In `build.gradle.kts`, inside the `intellijPlatform { }` block's `dependencies { intellijPlatform { ... } }` section (around line 121–126), add after `bundledPlugin("com.intellij.java")`:

  ```kotlin
  optionalPlugin("com.intellij.mcpServer")
  ```

  > **Why `optionalPlugin` not `plugin`**: `plugin()` adds a hard compilation and runtime dependency. `optionalPlugin()` makes classes available for compilation but marks the dep as optional so the plugin loads without it.

- [ ] **Step 4: Verify the build still compiles (before writing the toolset)**

  Run: `./gradlew compileKotlin 2>&1 | tail -20`

  Expected: `BUILD SUCCESSFUL` (the `FirefoxProfilerMcpToolset` class doesn't exist yet, but we haven't added it to mcp.xml either — the extension point registration will fail at plugin load time, not compile time; we'll add the class in Task 4).

  > Actually, we skip this compile check here since `mcp.xml` already references `FirefoxProfilerMcpToolset`. The Gradle build doesn't validate XML extension references. Full build validation happens after Task 4.

- [ ] **Step 5: Commit**

  ```bash
  git add src/main/resources/META-INF/plugin.xml \
          src/main/resources/META-INF/mcp.xml \
          build.gradle.kts
  git commit -m "build: add optional com.intellij.mcpServer dependency and mcp.xml"
  ```

---

## Task 3: Implement McpBridge

`McpBridge` is a stateless helper that provides the toolset with all runtime state: active JFR file path, active `WebViewWindow`, `AttachService`, and `JeffreyLauncher`. It does NOT hold references itself — it reads from live IntelliJ services each call.

**Key APIs used:**
- `FileEditorManager.getInstance(project).selectedEditor` — returns the active editor; cast to `JFRFileEditor` if applicable
- `JFRFileEditor.webViewWindow` — the public property exposed in Task 1 is already there (it's `val webViewWindow` — public)
- `AttachService.getInstance(project)` — the attach/recording service
- `JeffreyLauncher.isJdkAvailable()` — check JDK 25+ availability
- `Server` class from jfrtofp-server: `me.bechberger.jfrtofp.server.Server` — already on classpath

**Files:**
- Create: `src/main/kotlin/me/bechberger/jfrplugin/mcp/McpBridge.kt`

- [ ] **Step 1: Create the file**

  Create `src/main/kotlin/me/bechberger/jfrplugin/mcp/McpBridge.kt`:

  ```kotlin
  package me.bechberger.jfrplugin.mcp

  import com.intellij.openapi.fileEditor.FileEditorManager
  import com.intellij.openapi.project.Project
  import com.intellij.openapi.vfs.LocalFileSystem
  import me.bechberger.jfrtofp.server.Server
  import me.bechberger.jfrplugin.attach.AttachService
  import me.bechberger.jfrplugin.config.profilerConfig
  import me.bechberger.jfrplugin.editor.JFRFileEditor
  import me.bechberger.jfrplugin.viewer.JeffreyLauncher
  import java.nio.file.Path
  import kotlin.io.path.absolutePathString

  object McpBridge {

      /** Returns the active JFRFileEditor, or null if none is open. */
      fun activeJfrEditor(project: Project): JFRFileEditor? =
          FileEditorManager.getInstance(project).selectedEditor as? JFRFileEditor

      /** Returns the current URL loaded in the Firefox Profiler browser, or null. */
      fun currentUrl(project: Project): String? =
          activeJfrEditor(project)?.webViewWindow?.currentUrl

      /** Returns the absolute path of the currently open JFR/json.gz file, or null. */
      fun currentFilePath(project: Project): String? =
          activeJfrEditor(project)?.file?.path

      /**
       * Opens a JFR file at [path] in the Firefox Profiler.
       * Starts jfrtofp-server if needed, then loads the resulting URL.
       * Returns the URL on success, or throws on error.
       */
      fun openJfr(project: Project, path: String): String {
          val file = Path.of(path)
          val config = project.profilerConfig.conversionConfig.toConfig()
          val url = Server.startIfNeededAndGetUrl(file, config, null, null)
          val vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file)
              ?: error("File not found: $path")
          com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
              FileEditorManager.getInstance(project).openFile(vf, true)
          }
          // Wait briefly for the editor to open, then load the URL
          Thread.sleep(300)
          activeJfrEditor(project)?.webViewWindow?.loadUrl(url)
          return url
      }

      /** Loads a new URL in the active Firefox Profiler browser. Throws if no editor is open. */
      fun loadUrl(project: Project, url: String) {
          val editor = activeJfrEditor(project) ?: error("No JFR file is currently open in the editor.")
          editor.webViewWindow.loadUrl(url)
      }

      fun attachService(project: Project): AttachService = AttachService.getInstance(project)

      fun isJeffreyAvailable(): Boolean = JeffreyLauncher.isJdkAvailable()

      fun isServerRunning(): Boolean = try {
          Server.getInstance(null, null, null, null, false) != null
      } catch (_: Exception) {
          false
      }
  }
  ```

- [ ] **Step 2: Check that `profilerConfig` is an extension property on Project**

  Run: `grep -rn "profilerConfig" src/main/kotlin/me/bechberger/jfrplugin/config/`

  Expected: Find `val Project.profilerConfig: ProfilerConfig` or similar. If the extension property name differs, update the import and usage in McpBridge.kt accordingly.

- [ ] **Step 3: Verify McpBridge compiles (without the toolset yet)**

  Run: `./gradlew compileKotlin 2>&1 | grep -E "error|warning|BUILD"`

  Expected: `BUILD SUCCESSFUL` (McpBridge doesn't reference MCP SDK classes so it should compile regardless of the optional dep).

  If compile error about `Server.getInstance` signature: open `jfrtofp-server`'s `Server.java` to confirm the correct overload to check server existence. Alternative: wrap in `try { Server.startIfNeededAndGetUrl(Path.of("/dev/null"), null, null, null); true } catch(_: Exception) { false }` — but this is ugly. Better: just return `true` if any cached server port > 0 by using reflection, or simplest: check via `Server.startIfNeededAndGetUrl` with an empty path catch. The simplest approach that's always safe:

  ```kotlin
  fun isServerRunning(): Boolean = runCatching {
      // Server.getInstance is private; proxy via reflection or just always report based on whether
      // currentUrl is non-null (server must be running if a URL is loaded)
      currentUrl(project = com.intellij.openapi.project.ProjectManager.getInstance().openProjects.firstOrNull() ?: return false) != null
  }.getOrDefault(false)
  ```

  Actually, `isServerRunning` is called with a project in `profiler_get_status` — so we have project context. Use `currentUrl(project) != null` as the proxy. Update `McpBridge`:

  ```kotlin
  fun isServerRunning(project: Project): Boolean = currentUrl(project) != null
  ```

- [ ] **Step 4: Commit**

  ```bash
  git add src/main/kotlin/me/bechberger/jfrplugin/mcp/McpBridge.kt
  git commit -m "feat: add McpBridge for MCP tool runtime state access"
  ```

---

## Task 4: Implement FirefoxProfilerMcpToolset

The toolset class is annotated with `@McpToolset` (or implements the `McpToolset` interface — check IntelliJ MCP SDK API) and exposes 7 `suspend fun`s annotated with `@McpTool` and `@McpDescription`.

**Background on IntelliJ MCP API (2025.1):**
- Package: `com.intellij.mcpServer` (available from IntelliJ 2025.1+ with AI Assistant or standalone MCP plugin)
- A toolset implements `McpToolset` or is annotated with `@McpToolset`  
- Tool methods are `suspend fun` annotated `@McpTool` with `@McpDescription` on parameters
- Methods return `String` (plain text or JSON)
- Every method must catch exceptions and return `"Error: <msg>"` — never throw

**Finding the exact API:** Since `com.intellij.mcpServer` is optional, check the IntelliJ SDK docs or the plugin's source at: `~/.gradle/caches/` after running `./gradlew dependencies`. The key types to import:
- `com.intellij.mcpServer.McpToolset`  
- `com.intellij.mcpServer.McpTool` (annotation)
- `com.intellij.mcpServer.McpDescription` (annotation)

**Files:**
- Create: `src/main/kotlin/me/bechberger/jfrplugin/mcp/FirefoxProfilerMcpToolset.kt`

- [ ] **Step 1: Find the exact MCP SDK API**

  Run: `find ~/.gradle/caches -name "mcpServer*.jar" 2>/dev/null | head -5`

  If found: `jar tf <path> | grep -i "Toolset\|McpTool\|McpDescription" | head -20`

  This reveals the exact class names and package paths. If not found:

  Run: `./gradlew dependencies --configuration compileClasspath 2>&1 | grep -i mcp`

  Note the exact API. The most likely API based on IntelliJ plugin SDK conventions:
  - Interface: `com.intellij.mcpServer.McpToolset`
  - Annotations: `@com.intellij.mcpServer.McpTool`, `@com.intellij.mcpServer.McpDescription`

  > If the API is different from what's shown below, adjust the class/annotation names in the implementation code accordingly.

- [ ] **Step 2: Create `FirefoxProfilerMcpToolset.kt`**

  Create `src/main/kotlin/me/bechberger/jfrplugin/mcp/FirefoxProfilerMcpToolset.kt`:

  ```kotlin
  package me.bechberger.jfrplugin.mcp

  import com.intellij.mcpServer.McpDescription
  import com.intellij.mcpServer.McpTool
  import com.intellij.mcpServer.McpToolset
  import com.intellij.openapi.project.Project
  import kotlinx.serialization.encodeToString
  import kotlinx.serialization.json.*
  import me.bechberger.jfrplugin.attach.AttachService
  import me.bechberger.jfrplugin.util.PsiUtils

  class FirefoxProfilerMcpToolset(private val project: Project) : McpToolset {

      @McpTool
      @McpDescription("Open a JFR recording in the embedded Firefox Profiler.")
      suspend fun profiler_open_jfr(
          @McpDescription("Absolute filesystem path to the JFR recording file.")
          path: String
      ): String = runCatching {
          McpBridge.openJfr(project, path)
          "Opened $path"
      }.getOrElse { "Error: ${it.message}" }

      @McpTool
      @McpDescription("""
          Start a JFR recording for a running JVM.
          Attaches to the specified PID, or to the most recently launched
          run configuration if no PID is given.
      """)
      suspend fun profiler_start_recording(
          @McpDescription("PID of the JVM to profile. Omit to use the most recently launched run configuration.")
          pid: String? = null
      ): String = runCatching {
          val attachService = McpBridge.attachService(project)
          val targetPid = pid ?: attachService.listJvms().lastOrNull()?.pid
              ?: return "Error: no PID given and no JVMs found"
          attachService.start(targetPid, AttachService.Engine.JFR)
          "Recording started for PID $targetPid"
      }.getOrElse { "Error: ${it.message}" }

      @McpTool
      @McpDescription("""
          Stop the current JFR recording and open the result in the Firefox Profiler.
          Returns the path to the saved JFR file.
      """)
      suspend fun profiler_stop_recording(
          @McpDescription("PID of the JVM whose recording to stop. Required.")
          pid: String
      ): String = runCatching {
          val attachService = McpBridge.attachService(project)
          val outputFile = attachService.stop(pid)
          McpBridge.openJfr(project, outputFile.toString())
          outputFile.toString()
      }.getOrElse { "Error: ${it.message}" }

      @McpTool
      @McpDescription("""
          Navigate the embedded Firefox Profiler to a specific view.
          Supported views: flame_graph, call_tree, stack_chart, marker_chart, marker_table, network_chart.
      """)
      suspend fun profiler_navigate(
          @McpDescription("View name: flame_graph | call_tree | stack_chart | marker_chart | marker_table | network_chart")
          view: String
      ): String = runCatching {
          val validViews = setOf("flame_graph", "call_tree", "stack_chart", "marker_chart", "marker_table", "network_chart")
          if (view !in validViews) return "Error: unknown view '$view'. Valid views: ${validViews.joinToString()}"
          val currentUrl = McpBridge.currentUrl(project)
              ?: return "Error: no JFR file is currently open in the editor."
          val newUrl = buildViewUrl(currentUrl, view)
          McpBridge.loadUrl(project, newUrl)
          "Navigated to $view"
      }.getOrElse { "Error: ${it.message}" }

      @McpTool
      @McpDescription("""
          Return the current state of the profiler: whether a JFR file is open,
          which file, whether a recording is in progress, and whether Jeffrey is available.
      """)
      suspend fun profiler_get_status(): String = runCatching {
          val attachService = McpBridge.attachService(project)
          val recordingPids = attachService.listJvms()
              .filter { attachService.state(it.pid) is AttachService.RecordingState.Recording }
              .map { it.pid }
          Json.encodeToString(buildJsonObject {
              put("jfrtofpServerRunning", McpBridge.isServerRunning(project))
              put("currentFile", McpBridge.currentFilePath(project))
              put("currentUrl", McpBridge.currentUrl(project))
              put("recordingInProgress", recordingPids.isNotEmpty())
              put("recordingPids", Json.encodeToJsonElement(recordingPids))
              put("jeffreyAvailable", McpBridge.isJeffreyAvailable())
          })
      }.getOrElse { "Error: ${it.message}" }

      @McpTool
      @McpDescription("""
          Return the top N hottest functions from the currently open JFR profile.
          Each entry includes function name, file, sample count, and percentage.
      """)
      suspend fun profiler_get_hot_functions(
          @McpDescription("Number of functions to return. Default: 20.")
          n: Int = 20
      ): String = runCatching {
          val filePath = McpBridge.currentFilePath(project)
              ?: return "Error: no JFR file is currently open in the editor."
          HotFunctionExtractor.extract(java.nio.file.Path.of(filePath), n)
      }.getOrElse { "Error: ${it.message}" }

      @McpTool
      @McpDescription("""
          Navigate the IDE editor to the source of a function.
          Accepts fully-qualified class name, optionally with method name separated by '#'.
      """)
      suspend fun profiler_open_function(
          @McpDescription("Fully-qualified class name, e.g. com.example.Foo or com.example.Foo#processRequest")
          fqn: String
      ): String = runCatching {
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
  ```

  > **Note on `profiler_start_recording` pid type**: The spec says `pid: Int? = null`. IntelliJ MCP tool parameters may not support nullable Int well depending on SDK version. Using `String?` is safer — we convert to Int internally when AttachService.start() expects String (AttachService uses String PIDs throughout).

  > **Note on `profiler_get_hot_functions`**: This delegates to `HotFunctionExtractor` which we create in Task 5.

- [ ] **Step 3: Verify it compiles**

  Run: `./gradlew compileKotlin 2>&1 | grep -E "error:|BUILD"`

  If error about `McpToolset`/`@McpTool`/`@McpDescription` not found:
  1. The optional dep may not be downloaded yet. Run: `./gradlew dependencies 2>&1 | grep mcpServer`
  2. Check the exact package name in the downloaded jar:
     `find ~/.gradle/caches -name "*.jar" | xargs -I{} jar tf {} 2>/dev/null | grep -i "mcptoolset\|mcp_toolset" | head -5`
  3. Adjust import paths accordingly.

  If error about `HotFunctionExtractor` not found: that's expected — it's created in Task 5. For now, temporarily stub it:
  ```kotlin
  // Temporary stub — remove after Task 5
  private object HotFunctionExtractor {
      fun extract(path: java.nio.file.Path, n: Int): String = "[]"
  }
  ```

- [ ] **Step 4: Run full build**

  Run: `./gradlew clean buildPlugin 2>&1 | tail -30`

  Expected: `BUILD SUCCESSFUL` and `build/distributions/jfrplugin-*.zip` exists.

- [ ] **Step 5: Commit**

  ```bash
  git add src/main/kotlin/me/bechberger/jfrplugin/mcp/
  git commit -m "feat: add FirefoxProfilerMcpToolset with 6 of 7 tools (hot functions pending)"
  ```

---

## Task 5: Implement HotFunctionExtractor

`profiler_get_hot_functions` must parse the current JFR file and extract the top-N sampled frames without requiring the browser to be open. The `jfrtofp` library is already a dependency — it can open JFR files.

**Approach:** Use JDK's `jdk.jfr.consumer.RecordingFile` (available since JDK 9, on the classpath in IntelliJ's JBR) to iterate over `jdk.ExecutionSample` events. Count occurrences of each method frame across all samples. Return sorted JSON.

**Files:**
- Create: `src/main/kotlin/me/bechberger/jfrplugin/mcp/HotFunctionExtractor.kt`

- [ ] **Step 1: Create `HotFunctionExtractor.kt`**

  Create `src/main/kotlin/me/bechberger/jfrplugin/mcp/HotFunctionExtractor.kt`:

  ```kotlin
  package me.bechberger.jfrplugin.mcp

  import jdk.jfr.consumer.RecordingFile
  import kotlinx.serialization.encodeToString
  import kotlinx.serialization.json.*
  import java.nio.file.Path

  object HotFunctionExtractor {

      fun extract(jfrPath: Path, n: Int): String {
          val counts = mutableMapOf<FrameKey, Int>()
          var totalSamples = 0

          RecordingFile(jfrPath).use { rf ->
              while (rf.hasMoreEvents()) {
                  val event = rf.readEvent()
                  if (event.eventType.name != "jdk.ExecutionSample" &&
                      event.eventType.name != "jdk.CPUTimeSample") continue
                  totalSamples++
                  val stackTrace = event.stackTrace ?: continue
                  val topFrame = stackTrace.frames.firstOrNull() ?: continue
                  val method = topFrame.method
                  val key = FrameKey(
                      function = "${method.type.name}::${method.name}",
                      file = method.type.name.replace('.', '/') + ".java",
                      line = topFrame.lineNumber
                  )
                  counts[key] = (counts[key] ?: 0) + 1
              }
          }

          val sorted = counts.entries.sortedByDescending { it.value }.take(n)
          return Json.encodeToString(buildJsonArray {
              for ((key, count) in sorted) {
                  add(buildJsonObject {
                      put("function", key.function)
                      put("file", key.file)
                      put("line", key.line)
                      put("samples", count)
                      put("percent", if (totalSamples > 0) (count * 100.0 / totalSamples) else 0.0)
                  })
              }
          })
      }

      private data class FrameKey(val function: String, val file: String, val line: Int)
  }
  ```

- [ ] **Step 2: Remove the temporary stub from FirefoxProfilerMcpToolset.kt**

  In `FirefoxProfilerMcpToolset.kt`, remove the temporary inner `HotFunctionExtractor` object if it was added in Task 4 Step 3.

- [ ] **Step 3: Verify it compiles**

  Run: `./gradlew compileKotlin 2>&1 | grep -E "error:|BUILD"`

  Expected: `BUILD SUCCESSFUL`

  If error about `jdk.jfr.consumer.RecordingFile` not found: The JDK Flight Recorder API is a JDK module (`--add-modules jdk.jfr` may be needed). Check if the build already imports JFR APIs:

  Run: `grep -rn "jdk.jfr\|RecordingFile" src/`

  If no other JFR API usage exists, add to `build.gradle.kts` under `tasks.withType<JavaCompile>`:
  ```kotlin
  options.compilerArgs += listOf("--add-exports", "jdk.jfr/jdk.jfr.consumer=ALL-UNNAMED")
  ```
  Similarly for Kotlin tasks. However, this is likely unnecessary since `jdk.jfr` is available in JBR 21 without explicit exports.

- [ ] **Step 4: Run full build and verify ZIP**

  Run: `./gradlew clean buildPlugin 2>&1 | tail -10`

  Expected: `BUILD SUCCESSFUL`

  Run: `unzip -l build/distributions/jfrplugin-*.zip | grep -E "junit|HotFunction|McpBridge|FirefoxProfiler"`

  Expected: `HotFunctionExtractor` and `McpBridge` and `FirefoxProfilerMcpToolset` classes present; no `junit` JARs.

- [ ] **Step 5: Smoke test: open the plugin in sandboxed IDE**

  Run: `./gradlew runIde 2>&1 &`

  Wait for IDE to open (~2 minutes). Open any `.jfr` file from `test-project/`. The file should open in Firefox Profiler as before. Close the IDE.

  This verifies: (1) plugin loads without the MCP dep and still works, (2) no class loading errors.

- [ ] **Step 6: Commit**

  ```bash
  git add src/main/kotlin/me/bechberger/jfrplugin/mcp/HotFunctionExtractor.kt \
          src/main/kotlin/me/bechberger/jfrplugin/mcp/FirefoxProfilerMcpToolset.kt
  git commit -m "feat: implement HotFunctionExtractor for profiler_get_hot_functions tool"
  ```

---

## Task 6: Finalize and push

- [ ] **Step 1: Final full build and ZIP check**

  Run: `./gradlew clean buildPlugin 2>&1 | tail -10`

  Expected: `BUILD SUCCESSFUL`

  Run: `unzip -l build/distributions/jfrplugin-*.zip | grep "\.class\|\.jar" | grep -E "Mcp|Bridge|Toolset|HotFunction"`

  Expected: all four MCP classes present.

- [ ] **Step 2: Push to trigger CI**

  Run: `git push origin main`

  CI runs at: https://github.com/parttimenerd/intellij-profiler-plugin/actions

- [ ] **Step 3: Verify CI passes**

  Run: `gh run list --repo parttimenerd/intellij-profiler-plugin --limit 3`

  Wait for the run to complete. Expected: green.

---

## Quick API Reference

### AttachService (package `me.bechberger.jfrplugin.attach`)
```kotlin
AttachService.getInstance(project): AttachService
attachService.listJvms(): List<JvmInfo>          // JvmInfo(pid: String, displayName: String)
attachService.state(pid: String): RecordingState  // Idle | Recording
attachService.start(pid: String, engine: Engine)  // Engine.JFR | Engine.ASYNC_PROFILER
attachService.stop(pid: String): Path             // returns output file
```

### McpBridge (created in Task 3)
```kotlin
McpBridge.activeJfrEditor(project): JFRFileEditor?
McpBridge.currentUrl(project): String?
McpBridge.currentFilePath(project): String?
McpBridge.openJfr(project, path: String): String   // returns URL
McpBridge.loadUrl(project, url: String)
McpBridge.attachService(project): AttachService
McpBridge.isJeffreyAvailable(): Boolean
McpBridge.isServerRunning(project): Boolean
```

### PsiUtils (package `me.bechberger.jfrplugin.util`)
```kotlin
PsiUtils.hasClass(project, fqn: String): Boolean
PsiUtils.navigateToClass(project, className: String, pkg: String, line: Int, method: String)
```

### WebViewWindow (Java, `me.bechberger.jfrplugin.editor`)
```java
webViewWindow.loadUrl(String url)
webViewWindow.getCurrentUrl(): String    // added in Task 1
webViewWindow.reload()
```
