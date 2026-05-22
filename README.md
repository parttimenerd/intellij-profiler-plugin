# Java Profiler Plugin

![Build](https://github.com/parttimenerd/intellij-profiler-plugin/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/20937-java-jfr-profiler.svg)](https://plugins.jetbrains.com/plugin/20937-java-jfr-profiler)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/20937-java-jfr-profiler.svg)](https://plugins.jetbrains.com/plugin/20937-java-jfr-profiler)

<!-- Plugin description -->

An open-source profiler plugin for JDK 11+ based on JFR, async-profiler
and [Firefox Profiler](https://github.com/firefox-devtools/profiler).

This plugin supports

- profiling Java applications directly from run configurations (JFR and async-profiler)
- attaching to already-running JVMs from the "JFR Recording" tool window —
  start and stop recordings without restarting the process
- viewing JFR files in IntelliJ using Firefox Profiler or Jeffrey
- flamegraphs, call trees, function tables, marker timelines, and more

This plugin is currently under active development; feel free to try it and open issues for any bugs or suggestions.

<!-- Plugin description end -->

See my blog post [Firefox Profiler beyond the web](https://mostlynerdless.de/blog/2023/01/31/firefox-profiler-beyond-the-web/)
for a detailed description and how I ended up there.

## Features

### Profile from run configurations

The plugin adds a "Profile with JFR" and "Profile with async-profiler" action to all Java run configurations.
After the run finishes the profile opens automatically in the editor.

My plugin adds a context menu for profiling:

![Context Menu](https://mostlynerdless.de/wp-content/uploads/2023/01/Screenshot-2023-01-27-at-12.31.55-2048x1230.png)

### Attach to running JVMs

The "JFR Recording" tool window (View → Tool Windows → JFR Recording) lists all local JVMs.
For each process you can start a JFR or async-profiler recording with one click and stop it when done —
no restart required. The resulting JFR file opens automatically.

### View JFR files

Opening any `.jfr` file shows it in the built-in viewer. You can switch between
[Firefox Profiler](https://github.com/firefox-devtools/profiler) and
[Jeffrey](https://github.com/petrbouda/jeffrey) using the dropdown in the editor toolbar.

Right-click a `.jfr` file in the project tree and choose **Open with Jeffrey** to open it directly in Jeffrey.

![Opened Profile](https://mostlynerdless.de/wp-content/uploads/2023/01/Screenshot-2023-01-27-at-12.32.55-2048x1230.png)

You can double-click on a method in the profiler to navigate to it in the IDE, or
shift-double-click to view the source code directly in the profiler:

![Source Code View](https://mostlynerdless.de/wp-content/uploads/2023/01/Screenshot-2023-01-27-at-12.34.45-1-2048x1230.png)

All JFR events are visible as marker tracks:

![JFR Event View](https://mostlynerdless.de/wp-content/uploads/2023/01/Screenshot-2023-01-27-at-12.33.49-2048x1230.png)

## Installation

Available as [Java JFR Profiler](https://plugins.jetbrains.com/plugin/20937-java-jfr-profiler)
in the JetBrains marketplace.

You can also download the latest version from
[here](https://github.com/parttimenerd/intellij-profiler-plugin/releases/download/latest/Java.JFR.Profiler-all.jar)
and install it manually via **Settings → Plugins → Install Plugin from Disk**.

To enable the Jeffrey viewer, run `./gradlew downloadJeffrey` once after cloning.

## Architecture

The plugin is a thin wrapper around [jfrtofp-server](https://github.com/parttimenerd/jfrtofp-server),
which bundles the [JFR → Firefox Profiler converter](https://github.com/parttimenerd/jfrtofp) and a
[custom Firefox Profiler build](https://github.com/parttimenerd/firefox-profiler/tree/merged)
with Java-specific features not yet upstream.

Async-profiler support uses [ap-loader](https://github.com/jvm-profiling-tools/ap-loader).
JVM attach uses the standard `com.sun.tools.attach` API and `jdk.management.jfr.FlightRecorderMXBean`.

## Goals
- run JFR or async-profiler on all Java run configurations with a single click
- attach to running JVMs without restarting
- be the bridge between simple built-in profilers and advanced tools like JDK Mission Control

## Non goals
- fully fledged analysis
- JDK Mission Control replacement