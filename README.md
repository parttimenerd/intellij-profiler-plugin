# Java Profiler Plugin

![Build](https://github.com/parttimenerd/intellij-profiler-plugin/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/PLUGIN_ID.svg)](https://plugins.jetbrains.com/plugin/PLUGIN_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/PLUGIN_ID.svg)](https://plugins.jetbrains.com/plugin/PLUGIN_ID)

<!-- Plugin description -->

A profiler plugin for JDK 11+ based on JFR and [Firefox Profiler](https://github.com/firefox-devtools/profiler).

It allows you to profile your Java application with JFR and async-profiler and view the results in IntelliJ IDEA,
as well as opening JFR files.

It is essentially a thin wrapper around the [jfrtofp-server](https://github.com/parttimenerd/jfrtofp-server) library
which is a bundle of the [JFR to FirefoxProfiler converter](https://github.com/parttimenerd/jfrtofp) and a
[custom firefox profiler distribution](https://github.com/parttimenerd/firefox-profiler/tree/merged)
which includes many of our own PRs which are not yet upstream (and might be less stable).

It uses [ap-loader](https://github.com/jvm-profiling-tools/ap-loader) for async-profiler integration.

This plugin is currently in a very early stage, it might run into out of memory issues and not all expected
features are implemented yet. I tested it only on Mac so far, but it should work on other Unixes too.
Windows users might still encounter issues.

It is the first and only open source IntelliJ profiler plugin, so give it a try!

For simplicity, no configuration of the profiling is possible.
This might change, when the need arises. The plugin currently stores the gathered
profile in a `profile.jfr` file in the project root. It uses the profiling configuration of JFR
and wall clock profiling with jfrsync for async-profiler.

<!-- Plugin description end -->

## Goals
- run JFR with some settings on all run configurations
- be the bridge between simpler tools (like the profiler integrated into IntelliJ Ultimate) and the more advanced
  tools (like JDK Mission Control)

## Non goals
- fully fledged analysis
- JDK Mission Control replacement

## Template ToDo list
- [x] Create a new [IntelliJ Platform Plugin Template][template] project.
- [x] Get familiar with the [template documentation][template].
- [x] Verify the [pluginGroup](/gradle.properties), [plugin ID](/src/main/resources/META-INF/plugin.xml) and [sources package](/src/main/kotlin).
- [x] Review the [Legal Agreements](https://plugins.jetbrains.com/docs/marketplace/legal-agreements.html).
- [ ] [Publish a plugin manually](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html?from=IJPluginTemplate) for the first time.
- [ ] Set the Plugin ID in the above README badges.
- [ ] Set the [Deployment Token](https://plugins.jetbrains.com/docs/marketplace/plugin-upload.html).

## Installation

- Using IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "Java Profiler Plugin"</kbd> >
  <kbd>Install Plugin</kbd>

- Manually:

  Download the [latest release](https://github.com/parttimenerd/intellij-profiler-plugin/releases/download/latest/Java.Profiler.Plugin-all.jar) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>


---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template


TODO
====
- [x] fix unknown threads
- [x] support opening .json.gz files
- [x] add ap-loader support
- [x] fix startup profile files problem
- [x] add support for custom JFR configs
- [x] add support for custom ap-loader configs
  - both with extra files in the project folder
- [ ] fileGetter and navigate for proper IDE integration
  - [x] basic support for simple classes
  - [ ] support for inner classes
  - [ ] support for anonymous classes, lambdas, ...
  - [ ] support for decompiled classes
  - [ ] support for kotlin
- [x] project boundaries
  - implemented via conversion config, so user defines it