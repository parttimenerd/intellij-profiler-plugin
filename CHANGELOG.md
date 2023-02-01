<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Java Profiler Plugin

## [Unreleased]

## [0.0.3]

### Added
- Remove profile files before reprofiling

### Fixed
- Fix NullPointerException when opening the profile file after profiling #5
  - Thanks to @JohannesLichtenberger for reporting the issue
- Fix Firefox Profiler file type recognition
- Only add "-XX:FlightRecorder" in JDK12 and below

## [0.0.2-beta]

- Implement minimal viable product
- Support async-profiler using [ap-loader](https://github.com/jvm-profiling-tools/ap-loader) on supported platforms

## [0.0.1] - 2022-09-15
- Initial project scaffold
- Initially based on the Panda plugin by Ratislav Papp (https://bitbucket.org/rastislavpapp/panda)
