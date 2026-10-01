# MCP Toolset for JFR Plugin — Design Spec

## Goal

Contribute a `FirefoxProfilerMcpToolset` to IntelliJ's built-in MCP Server so that JetBrains AI, GitHub Copilot, Claude Code, and other coding agents can open JFR files, start/stop profiling runs, navigate the embedded Firefox Profiler, and inspect profiling results.

## Architecture

The plugin contributes a single `McpToolset` implementation via the `com.intellij.mcpServer.mcpToolset` extension point. IntelliJ's MCP Server handles all protocol/transport concerns (JSON-RPC, stdio, session management). The toolset itself is stateless — all runtime state lives in existing plugin services (`JeffreyLauncher`, `AttachService`) and `WebViewWindow`.

**The MCP dependency is optional.** The plugin must continue to load and work on IDEs without `com.intellij.mcpServer` (pre-2025.2, or without AI Assistant installed). This is done via an XML plugin split: MCP registration lives in a separate `mcp.xml` loaded only when the MCP plugin is present.

### Viewer context

- **Primary viewer**: Firefox Profiler via `jfrtofp-server` (`WebViewWindow` / `JFRFileEditor`) — always available
- **Secondary viewer**: Jeffrey (`JeffreyBrowserWindow`) — optional, requires JDK 25+
- MCP tools target the **Firefox Profiler viewer** (primary). Jeffrey support is noted where it differs.

### New files

| File | Purpose |
|---|---|
| `src/main/kotlin/me/bechberger/jfrplugin/mcp/FirefoxProfilerMcpToolset.kt` | The `McpToolset` implementation with all 7 tools |
| `src/main/kotlin/me/bechberger/jfrplugin/mcp/McpBridge.kt` | Thin bridge — accesses `WebViewWindow`, `JeffreyLauncher`, `AttachService` from a project context |
| `src/main/resources/META-INF/mcp.xml` | Optional XML: registers toolset extension when `com.intellij.mcpServer` is present |

### Changed files

| File | Change |
|---|---|
| `src/main/resources/META-INF/plugin.xml` | Add `<depends optional="true" config-file="mcp.xml">com.intellij.mcpServer</depends>` |
| `build.gradle.kts` | Add `optionalPlugin("com.intellij.mcpServer")` to `intellijPlatform { }` deps |
| `src/main/java/me/bechberger/jfrplugin/editor/WebViewWindow.java` | Add `getCurrentUrl(): String` and `loadUrl(url: String)` public methods (already exist privately) |
| `src/main/kotlin/me/bechberger/jfrplugin/editor/JFRFileEditor.kt` | Expose `webViewWindow` so `McpBridge` can reach it |

---

## The 7 Tools

### 1. `profiler_open_jfr`

```kotlin
@McpTool
@McpDescription("Open a JFR recording in the embedded Firefox Profiler.")
suspend fun profiler_open_jfr(
    @McpDescription("Absolute filesystem path to the JFR recording file.")
    path: String
): String
```

**Implementation**: Call `Server.startIfNeededAndGetUrl(file, config, ...)` then load the resulting URL in `WebViewWindow`. If no editor is currently open, open a new virtual file editor for the JFR file via `FileEditorManager.openFile()`. Returns `"Opened $path"` on success or an error string.

---

### 2. `profiler_start_recording`

```kotlin
@McpTool
@McpDescription("""
    Start a JFR recording for a running JVM.
    Attaches to the specified PID, or to the most recently launched
    run configuration if no PID is given.
""")
suspend fun profiler_start_recording(
    @McpDescription("PID of the JVM to profile. Omit to use the most recently launched run configuration.")
    pid: Int? = null
): String
```

**Implementation**: Delegates to `AttachService.startRecording(pid)`. Returns the recording ID or an error.

---

### 3. `profiler_stop_recording`

```kotlin
@McpTool
@McpDescription("""
    Stop the current JFR recording and open the result in the Firefox Profiler.
    Returns the path to the saved JFR file.
""")
suspend fun profiler_stop_recording(): String
```

**Implementation**: `AttachService.stopRecording()` → returns JFR file path → calls `profiler_open_jfr(path)` internally to open it. Returns the file path.

---

### 4. `profiler_navigate`

```kotlin
@McpTool
@McpDescription("""
    Navigate the embedded Firefox Profiler to a specific view.
    Supported views: flame_graph, call_tree, stack_chart, marker_chart, marker_table, network_chart.
""")
suspend fun profiler_navigate(
    @McpDescription("View name: flame_graph | call_tree | stack_chart | marker_chart | marker_table | network_chart")
    view: String
): String
```

**Implementation**: The Firefox Profiler URL encodes the current view as a query parameter. Get the current URL from `WebViewWindow.getCurrentUrl()`, replace the `?view=` fragment, then call `WebViewWindow.loadUrl(newUrl)`. Returns `"Navigated to $view"`.

---

### 5. `profiler_get_status`

```kotlin
@McpTool
@McpDescription("""
    Return the current state of the profiler: whether a JFR file is open,
    which file, whether a recording is in progress, and whether Jeffrey is available.
""")
suspend fun profiler_get_status(): String
```

**Implementation**: Returns a JSON string with:
- `jfrtofpServerRunning: Boolean` — `Server.isRunning()`
- `currentFile: String?` — path of the currently open JFR/json.gz file (from active editor)
- `currentUrl: String?` — `WebViewWindow.getCurrentUrl()`
- `recordingInProgress: Boolean` — `AttachService.isRecording()`
- `jeffreyAvailable: Boolean` — `JeffreyLauncher.isJdkAvailable()`

---

### 6. `profiler_get_hot_functions`

```kotlin
@McpTool
@McpDescription("""
    Return the top N hottest functions from the currently open JFR profile.
    Each entry includes function name, file, sample count, and percentage.
""")
suspend fun profiler_get_hot_functions(
    @McpDescription("Number of functions to return. Default: 20.")
    n: Int = 20
): String
```

**Implementation**: Get the current JFR file path from the active editor. Use the existing `jfrtofp` library (already a dependency) to parse the JFR and extract the top-N sampled frames from `SamplesTable`. Return a JSON array of `{function, file, line, samples, percent}`. Does not require the browser to be open.

---

### 7. `profiler_open_function`

```kotlin
@McpTool
@McpDescription("""
    Navigate the IDE editor to the source of a function.
    Accepts fully-qualified class name, optionally with method name.
""")
suspend fun profiler_open_function(
    @McpDescription("Fully-qualified class name, e.g. com.example.Foo or com.example.Foo#processRequest")
    fqn: String
): String
```

**Implementation**: Reuses the existing `PsiUtils.navigateToClass(project, fqn)` logic from `JeffreyIdeJumpService`. Returns `"Navigated to $fqn"` or an error if not found.

---

## Error handling

Every tool returns a plain string. On error, return a string starting with `"Error: "` followed by a short description. Do not throw exceptions out of tool methods.

## Accessing the active WebViewWindow

`McpBridge` gets the active `JFRFileEditor` from `FileEditorManager.getInstance(project).selectedEditor`, casts it to `JFRFileEditor`, and calls the new `webViewWindow` getter. If no JFR editor is active, tools that require it return `"Error: no JFR file is currently open in the editor."`.

## Optional dependency — mcp.xml

```xml
<!-- src/main/resources/META-INF/mcp.xml -->
<idea-plugin>
  <extensions defaultExtensionNs="com.intellij">
    <mcpServer.mcpToolset
        implementation="me.bechberger.jfrplugin.mcp.FirefoxProfilerMcpToolset"/>
  </extensions>
</idea-plugin>
```

In `plugin.xml`:
```xml
<depends optional="true" config-file="mcp.xml">com.intellij.mcpServer</depends>
```

This means: if `com.intellij.mcpServer` is absent, `mcp.xml` is silently skipped and the toolset is never registered. The plugin still loads cleanly.

## What is NOT in scope

- Jeffrey navigation (Jeffrey is optional; tools target Firefox Profiler)
- `profiler_get_call_tree`, `profiler_get_markers`, `profiler_search_functions` — deferred to a follow-up
- Any bundled UI for the MCP server itself
- Streaming / long-running tool support
