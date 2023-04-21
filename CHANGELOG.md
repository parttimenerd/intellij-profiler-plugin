<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Java JFR Profiler

## [Unreleased]

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
