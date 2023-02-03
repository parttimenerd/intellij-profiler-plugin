package me.bechberger.jfrplugin.util

import one.profiler.AsyncProfilerLoader

fun isAsyncProfilerSupported() = try {
    AsyncProfilerLoader.isSupported()
} catch (_: Throwable) {
    false
}
