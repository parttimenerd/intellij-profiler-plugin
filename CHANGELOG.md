<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Java JFR Profiler

## [Unreleased]

## [0.0.24]

### Fixed
- Fix `ClassNotFoundException: JBCefBrowserBuilder` on IntelliJ 2026.2+: JCEF moved to a
  separate bundled plugin (`com.intellij.modules.jcef`) in 2026.2; the plugin now declares
  an optional dependency so file editors are only registered when JCEF is available
- Fix `NoClassDefFoundError: Types$Predefined` when opening `.cjfr` files via Jeffrey:
  the JMC `flightrecorder.writer` transitive dependency was not bundled by `condensed-data`

## [0.0.23]

### Fixed
- Sampling interval now computed via modal-bucket detection — avoids sub-millisecond
  noise events (GC, allocation) and multi-thread outliers inflating the estimate
- Marker field extraction corrected: STACKTRACE fields, `cause.time`, `_class`,
  TABLE format, JFR sentinel values
- `heapAddressBits` shown as integer `32` instead of hex `0x20`
- Execution samples now correctly attributed using `sampledThread` field (not
  `eventThread`), fixing 0-sample CJFR profiles
- Native/GC threads now uniquely identified via `osThreadId` when `javaThreadId=0`

### Changed
- Update jfrtofp to 0.0.13, jfrtofp-server to 0.0.14

## [0.0.22]

### Added
- MCP tools `profiler_list_run_configurations` and `profiler_run`: AI assistants can now list all run configurations and launch any of them (app, test, Gradle task, …) with JFR or async-profiler profiling — including specific async-profiler event types (`ap:cpu`, `ap:wall`, `ap:alloc`, `ap:ctimer`, …)
- MCP tools `profiler_jfr_view` and `profiler_jfr_print`: run the JDK `jfr` CLI on the currently open file to get tabular summaries (`hot-methods`, `gc`, `exceptions`, …) or raw event output; `view=help` lists all available views
- Support for `.cjfr` (condensed JFR) files in both the Firefox Profiler and Jeffrey viewers

### Fixed
- JFR profiles now open in the embedded Firefox Profiler instead of hitting GitHub Pages (which returned a 404 for deep URLs due to missing SPA routing support)
- Profile title now shows just the class/test name instead of the full JVM command line (e.g. `CJFREventFieldAccessTest` instead of `com.intellij.rt.junit.JUnitStarter -ideVersion5 -junit5 …`)
- Firefox Profiler tab is now shown first (primary) when opening a JFR file
- Jeffrey viewer tab now uses a stable `JPanel` wrapper so the browser renders correctly
- Copilot `mcp.json` registration now uses a JSON library instead of regex, preventing malformed JSON that caused Copilot to silently ignore the server entry
- MCP server startup now calls `start()` directly instead of the Kotlin-mangled `settingsChanged` method

### Changed
- Update jfrtofp-server to 0.0.13 (embeds jfrtofp 0.0.10)

## [0.0.21]

### Added
- MCP toolset for JetBrains AI, GitHub Copilot, and Claude Code: open JFR files, start/stop profiling, navigate Firefox Profiler views, get hot functions, and navigate to source — all via MCP tools. Requires IntelliJ 2025.2+ with the MCP Server plugin; the plugin continues to work normally without it.
- CI test that loads all plugin-bundled JARs into an isolated classloader and verifies required runtime classes (Javalin, Jetty, Jackson) are present, preventing future missing-dependency regressions

### Fixed
- Opening a JFR file crashed with `NoClassDefFoundError: io/javalin/core/util/JavalinException` because Javalin, Jetty, and Jackson transitive dependencies of jfrtofp-server were missing from the plugin bundle ([#38](https://github.com/parttimenerd/intellij-profiler-plugin/issues/38))
- JUnit JARs no longer bundled in plugin (were pulled in transitively via jfrtofp-server)

### Changed
- Update jfrtofp-server to 0.0.10 (Javalin 4.6.7 → 7.2.3, Jetty 9 → 12)
- Update jfrtofp to 0.0.9
- Update Kotlin to 2.4.20
- Update ap-loader-all to 4.5-13
- Update jeffrey to 0.13.32
- Target IntelliJ 2025.2.5 platform (was 2025.1.2)

## [0.0.20] - 2026-07-17

### Fixed
- Profile viewer now uses [parttimenerd.github.io/firefox-profiler](https://parttimenerd.github.io/firefox-profiler) instead of the bundled local copy; share/upload still goes to `api.profiler.firefox.com`
- JVM version checks for `-Xlog:jfr+startup` and `-XX:+FlightRecorder` flags replaced fragile regex with direct integer comparison
- Jeffrey `extractJar()` no longer uses unreliable `InputStream.available()` for cache invalidation; jar is always overwritten on startup
- Jeffrey startup no longer uses a fixed 2-second sleep to detect immediate crashes; `waitForReady` loop handles it
- JSON body in Jeffrey `importFromPath` now built with `kotlinx.serialization` (fixes Windows path backslash corruption)
- `JeffreyIdeJumpService` server socket registered as `Disposable` so it is closed when the project closes
- Recording state correctly restored in the attach tool window if `stop()` fails (was left as Idle on error)
- Last output file for a PID now persisted in `AttachService` instead of the panel-local table model; survives tool window close/reopen
- Gradle profiling init script now also injects JVM args into `JavaExec` tasks (in addition to `Test`), enabling profiling of `./gradlew run`

### Changed
- Update jfrtofp-server to 0.0.6

## [0.0.19] - 2026-06-25

### Fixed
- Memory tracks (Used heap / Committed heap) missing from profiles (#37) — jdk.GCHeapSummary was incorrectly included in the noisy-events filter in jfrtofp 0.0.7

### Changed
- Update jfrtofp to 0.0.8 and jfrtofp-server to 0.0.5

## [0.0.18] - 2026-06-03

### Added
- Attach to running JVMs from a new "JFR Recording" tool window (#36)
  - Lists all local JVMs with PID and name; search/filter by name or PID
  - Start JFR or async-profiler recordings per process without restarting
  - Stop and immediately open the resulting profile
  - Open previous recordings with Firefox Profiler or Jeffrey
  - Auto-refreshes every 5 s; scroll position preserved during updates
- [Jeffrey](https://github.com/petrbouda/jeffrey) profiler viewer integration (#32)
  - Bundle Jeffrey 0.9.4; auto-launch on a free port using the first JDK 25+ found on the system
  - Jeffrey options are hidden when no JDK 25+ is available
  - "Open with Jeffrey" context menu action on JFR files in the project tree
  - Supports selecting multiple JFR files or a folder — all recordings are uploaded at once
  - Single-file open navigates directly to the recording's profile view (no list overview)
  - Multiple files open Jeffrey's recordings list with all uploads available
  - Each "Open with Jeffrey" call opens a new tab; multiple Jeffrey tabs can coexist
  - IDE jump server: navigate from Jeffrey flame graph directly to source in the IDE
- Named JFR output files for attach recordings: `profile-<pid>-<name>.jfr`
- Enable `jdk.CPUTimeSample` event (JEP 509) by default when profiling with a Java 25+ JDK on Linux; recognized as an execution sample in the Firefox Profiler view

### Fixed
- Profiling JUnit and TestNG test run configurations now works (#29)
- "Could not download profile" after attach recording: use
  `setPredefinedConfiguration("profile")` instead of broken manual settings map
- Cache poisoning when JFR → JSON conversion fails mid-write (jfrtofp)

### Changed
- Upgrade IntelliJ platform target 2022.1 → 2025.1 (JBR 21)
- Migrate build from org.jetbrains.intellij 1.x to org.jetbrains.intellij.platform 2.6.0
- Upgrade Kotlin 1.9 → 2.1, Java toolchain 11 → 21
- Update ap-loader to 4.4-13 (async-profiler with built-in jattach)
- Update jfrtofp to 0.0.7 and jfrtofp-server to 0.0.4 — significantly faster and smaller JFR → Firefox Profiler conversion:
  - Adds `jdk.CPUTimeSample` support
  - Switch JFR parsing to [jafar](https://github.com/btraceio/jafar) for streaming reads
  - Output-side memory now bounded via per-thread spill-to-disk + k-way merge
  - Hot-path JSON emitted through a custom generator instead of `kotlinx.serialization`
  - Default-on output-size reductions: drop redundant marker `data["type"]`/`data["startTime"]`,
    drop `cause.time` ISO strings, quantize timestamps to 4 decimals, drop JFR sentinel longs,
    null-out empty `threadCPUDelta`, omit default-zero `eventDelay`
  - Default-off `DEFAULT_NOISY_EVENTS` bundle filters high-volume GC/metaspace/ZGC detail events
    (e.g. `jdk.ObjectAllocationInNewTLAB`, `jdk.ZStatisticsCounter`); typical conversions emit
    ~50–85% smaller gzipped JSON
  - Fix handling of files without execution samples (#30)

## [0.0.17]

### Changed
- Make plugin really compatible with all future IntelliJ versions

## [0.0.16]

### Changed
- Make plugin compatible with all future IntelliJ versions by not specifying an upper bound

## [0.0.15]

### Added
- Support IntelliJ 2025.2

### Changed

- Use new async-profiler version

## [0.0.14]

### Added
- Support IntelliJ 2025.1

### Changed
- Use new async-profiler version

## [0.0.13]

### Added
- Support IntelliJ 2024.2

## [0.0.12]

### Fixed
- Fixed some issues with processing JFR files #14
- Hopefully fix "minimumFractionDigits value is out of range" #24 by making it more robust

## [0.0.11]

### Added
- Support profiling Maven goals (including Quarkus and Spring Boot)
- Support profiling Quarkus Gradle tasks (Fixes #16)

### Fixed
- Fixed child nodes with same function name in call-tree
  - Fixed it in jfrtofp: https://github.com/parttimenerd/jfrtofp/issues/6

### Changed
- Updated dependencies #19

## [0.0.10]

### Added
- Support IntelliJ 2023.3

## [0.0.9]

### Added
- Support IntelliJ 2023.2

## [0.0.8]

### Fixed
- Removed errorHandler specification from plugin.xml #6

### Added
- Support IntelliJ 2023.1

## [0.0.7]
### Fixed
- Support Oracle JDK 11.0.6 and earlier #13
- Fix problems with opening profile.jfr when already open #11
- Improve opening JFR files from async-profiler (without `jfrsync`)

## [0.0.6]
### Fixed
- Fix `alloc` usage of async-profiler #8
- Fix detection of Java versions without support to turn off JFR logger #10
- Fix adding VM parameters to the run configuration #9

## [0.0.5]
### Fixed
- Fixed plugin logo color
  - It was different in the web and in Java renderings
- Fix Windows related issue #7

## [0.0.4]

### Changed
- Modify JFR file type

## [0.0.3]
### Added
- Remove profile files before reprofiling

### Fixed
- Fix NullPointerException when opening the profile file after profiling #5
  - Thanks to @JohannesLichtenberger for reporting the issue
- Fix Firefox Profiler file type recognition
- Only add "-XX:FlightRecorder" in JDK12 and below

## [0.0.2-beta]
### Added
- Implement minimal viable product
- Support async-profiler using [ap-loader](https://github.com/jvm-profiling-tools/ap-loader) on supported platforms

## [0.0.1] - 2022-09-15
### Added
- Initial project scaffold
- Initially based on the Panda plugin by Ratislav Papp (https://bitbucket.org/rastislavpapp/panda)