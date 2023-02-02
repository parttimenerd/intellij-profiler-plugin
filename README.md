# Java Profiler Plugin

![Build](https://github.com/parttimenerd/intellij-profiler-plugin/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/20937-java-jfr-profiler.svg)](https://plugins.jetbrains.com/plugin/20937-java-jfr-profiler)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/20937-java-jfr-profiler.svg)](https://plugins.jetbrains.com/plugin/20937-java-jfr-profiler)

<!-- Plugin description -->

An open-source profiler plugin for JDK 11+ based on JFR, async-profiler
and [Firefox Profiler](https://github.com/firefox-devtools/profiler).

This plugin supports

- profiling your Java applications directly in IntelliJ
  - with JFR and async-profiler
- viewing JFR files
- showing flamegraphs, call trees and more

It allows you to profile your Java application with JFR and async-profiler and view the results in IntelliJ IDEA,
as well as opening JFR files.

This plugin is currently under heavy development; feel free to try it and open issues for any bugs or suggestions.

<!-- Plugin description end -->

See my blog post [Firefox Profiler beyond the web](https://mostlynerdless.de/blog/2023/01/31/firefox-profiler-beyond-the-web/)
for a detailed description and how I ended up there.

In the following a few screenshots.

My plugin adds a context menu for profiling:

![Context Menu](https://mostlynerdless.de/wp-content/uploads/2023/01/Screenshot-2023-01-27-at-12.31.55-2048x1230.png)

And opens the profile automatically afterwards. But you can open arbitrary JFR files:

![Opened Profile](https://mostlynerdless.de/wp-content/uploads/2023/01/Screenshot-2023-01-27-at-12.32.55-2048x1230.png)

You can double-click on a method in the profiler and thereby navigate to the method in the IDE, or
you can shift-double-click and view the source code directly in the profiler, with some additional
information on the left:

![Source Code View](https://mostlynerdless.de/wp-content/uploads/2023/01/Screenshot-2023-01-27-at-12.34.45-1-2048x1230.png)

Besides that, it has support for showing information on all JFR events:

![JFR Event View](https://mostlynerdless.de/wp-content/uploads/2023/01/Screenshot-2023-01-27-at-12.33.49-2048x1230.png)

It is available as [Java JFR Profiler](https://plugins.jetbrains.com/plugin/20937-java-jfr-profiler)
in the JetBrains marketplace.

You can also download the latest version from
[here](https://github.com/parttimenerd/intellij-profiler-plugin/releases/download/latest/Java.JFR.Profiler-all.jar)
and install it manually in IntelliJ using the "Install Plugin from Disk" action.

It is essentially a thin wrapper around the [jfrtofp-server](https://github.com/parttimenerd/jfrtofp-server) library
which is a bundle of the [JFR to FirefoxProfiler converter](https://github.com/parttimenerd/jfrtofp) and a
[custom firefox profiler distribution](https://github.com/parttimenerd/firefox-profiler/tree/merged)
which includes many of our own PRs which are not yet upstream.

It uses [ap-loader](https://github.com/jvm-profiling-tools/ap-loader) for async-profiler integration.

It is the first fully fledged open source IntelliJ profiler plugin, so give it a try!

## Goals
- run JFR with some settings on all run configurations
- be the bridge between simpler tools (like the profiler integrated into IntelliJ Ultimate) and the more advanced
  tools (like JDK Mission Control)

## Non goals
- fully fledged analysis
- JDK Mission Control replacement

## License
MIT