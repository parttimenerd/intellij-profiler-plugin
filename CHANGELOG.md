<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Java JFR Profiler

## [Unreleased]

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
- Enable `jdk.CPUTimeSample` event (JEP 509) by default when profiling with a Java 25+ JDK; recognized as an execution sample in the Firefox Profiler view

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
- Update jfrtofp to 0.0.6-SNAPSHOT and jfrtofp-server to 0.0.4-SNAPSHOT

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