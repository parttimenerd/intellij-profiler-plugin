<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Java JFR Profiler

## [Unreleased]

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