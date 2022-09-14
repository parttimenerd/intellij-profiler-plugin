# Java Profiler Plugin

A profiler plugin for Java 11+ based on JFR and [Firefox Profiler](https://github.com/firefox-devtools/profiler)

![Build](https://github.com/parttimenerd/intellij-profiler-plugin/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/PLUGIN_ID.svg)](https://plugins.jetbrains.com/plugin/PLUGIN_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/PLUGIN_ID.svg)](https://plugins.jetbrains.com/plugin/PLUGIN_ID)



## Goals
- run JFR with some settings for all main-like functions
- present as (zoomable) flamegraph and tree view
- maybe: launch with JMC and store JFR file button
- using mostly existing views

## Non goals
- fully fledged analysis
- JMC replacement
- implementing lots of UI code

It was initially based on the Panda plugin by Ratislav Papp (https://bitbucket.org/rastislavpapp/panda)

## Ideas from Andreas
- [ ] use dates ("12:00:34.2") instead of just seconds ("34.2")
- [ ] skip methods with less than x % of time in the method tables and flamegraphs (e.g. focus on the main culprits in a method),
      map these to "other"

## Ideas from Oliver
- [ ] which JVM version, params
- [ ] how long does the GC take (wall clock time), mark long GC times with a different color
- [ ] maybe import GC History / GC History viewer

## Other Ideas
- [ ] use the whole descriptor in the method name and use the resource only for actual files (?)
- [ ] package converter as a separate JAR and repository, then package the server with the converter
- [ ] use relative times instead of absolute times
- [ ] fork the FirefoxProfiler (friendly)
- [ ] combine several markers into one (collect all Environment variables)

## Unsuccessful ideas
- using the (FlameViewer)[https://github.com/kornilova203/FlameViewer] for the flamegraphs
  - this project stalled and does not properly work with newer JDKs (> 8) and newer CPU architectures (aarch64)
  - getting it to run properly would be a lot of work
    - I would essentially become a kind of maintainer for this code then

## Template ToDo list
- [x] Create a new [IntelliJ Platform Plugin Template][template] project.
- [ ] Get familiar with the [template documentation][template].
- [ ] Verify the [pluginGroup](/gradle.properties), [plugin ID](/src/main/resources/META-INF/plugin.xml) and [sources package](/src/main/kotlin).
- [ ] Review the [Legal Agreements](https://plugins.jetbrains.com/docs/marketplace/legal-agreements.html).
- [ ] [Publish a plugin manually](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html?from=IJPluginTemplate) for the first time.
- [ ] Set the Plugin ID in the above README badges.
- [ ] Set the [Deployment Token](https://plugins.jetbrains.com/docs/marketplace/plugin-upload.html).
- [ ] Click the <kbd>Watch</kbd> button on the top of the [IntelliJ Platform Plugin Template][template] to be notified about releases containing new features and fixes.

<!-- Plugin description -->
This Fancy IntelliJ Platform Plugin is going to be your implementation of the brilliant ideas that you have.

This specific section is a source for the [plugin.xml](/src/main/resources/META-INF/plugin.xml) file which will be extracted by the [Gradle](/build.gradle.kts) during the build process.

To keep everything working, do not remove `<!-- ... -->` sections.
<!-- Plugin description end -->

## Installation

- Using IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "jfrplugin"</kbd> >
  <kbd>Install Plugin</kbd>

- Manually:

  Download the [latest release](https://github.com/parttimenerd/jfrplugin/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>


---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
