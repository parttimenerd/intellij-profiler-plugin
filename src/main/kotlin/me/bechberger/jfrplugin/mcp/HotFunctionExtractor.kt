package me.bechberger.jfrplugin.mcp

import jdk.jfr.consumer.RecordingFile
import kotlinx.serialization.json.*
import java.nio.file.Path

internal object HotFunctionExtractor {

    fun extract(jfrPath: Path, n: Int): String {
        val counts = mutableMapOf<FrameKey, Int>()
        var totalSamples = 0

        RecordingFile(jfrPath).use { rf ->
            while (rf.hasMoreEvents()) {
                val event = rf.readEvent()
                if (event.eventType.name != "jdk.ExecutionSample" &&
                    event.eventType.name != "jdk.CPUTimeSample") continue
                totalSamples++
                val stackTrace = event.stackTrace ?: continue
                val topFrame = stackTrace.frames.firstOrNull() ?: continue
                val method = topFrame.method
                val key = FrameKey(
                    function = "${method.type.name}::${method.name}",
                    file = method.type.name.replace('.', '/') + ".java",
                    line = topFrame.lineNumber
                )
                counts[key] = (counts[key] ?: 0) + 1
            }
        }

        val sorted = counts.entries.sortedByDescending { it.value }.take(n)
        return buildJsonArray {
            for ((key, count) in sorted) {
                add(buildJsonObject {
                    put("function", key.function)
                    put("file", key.file)
                    put("line", key.line)
                    put("samples", count)
                    put("percent", if (totalSamples > 0) (count * 100.0 / totalSamples) else 0.0)
                })
            }
        }.toString()
    }

    private data class FrameKey(val function: String, val file: String, val line: Int)
}
