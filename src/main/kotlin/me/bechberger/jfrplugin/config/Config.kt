package me.bechberger.jfrplugin.config

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.io.inputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import me.bechberger.jfrtofp.processor.Config
import java.nio.file.Files
import java.nio.file.Path

@OptIn(ExperimentalSerializationApi::class)
val jsonFormat = Json {
    prettyPrint = true
    encodeDefaults = true
    explicitNulls = false
}

@Serializable
data class JFRConfig(val settings: String = "profile")

@Serializable
data class AsyncProfilerConfig(
    val jfrsync: Boolean = true,
    val alloc: Boolean = true,
    val event: String = "wall",
    val misc: String = ""
)

@Serializable
data class ConversionConfig(
    val nonProjectPackagePrefixes: MutableList<String> = Config.DEFAULT_NON_PROJECT_PACKAGE_PREFIXES.toMutableList(),
    val enableMarkers: Boolean = true,
    val initialVisibleThreads: Int = Config.DEFAULT_INITIAL_VISIBLE_THREADS,
    val initialSelectedThreads: Int = Config.DEFAULT_INITIAL_SELECTED_THREADS,
    val includeGCThreads: Boolean = false,
    val includeInitialSystemProperty: Boolean = false,
    val includeInitialEnvironmentVariables: Boolean = false,
    val includeSystemProcesses: Boolean = false,
    val ignoredEvents: MutableList<String> = Config.DEFAULT_IGNORED_EVENTS.toMutableList(),
    val minRequiredItemsPerThread: Int = Config.DEFAULT_MIN_ITEMS_PER_THREAD
) {
    fun toConfig(): Config {
        return Config(
            nonProjectPackagePrefixes = nonProjectPackagePrefixes,
            enableMarkers = enableMarkers,
            initialVisibleThreads = initialVisibleThreads,
            initialSelectedThreads = initialSelectedThreads,
            includeGCThreads = includeGCThreads,
            includeInitialSystemProperty = includeInitialSystemProperty,
            includeInitialEnvironmentVariables = includeInitialEnvironmentVariables,
            includeSystemProcesses = includeSystemProcesses,
            ignoredEvents = ignoredEvents.toSet(),
            minRequiredItemsPerThread = minRequiredItemsPerThread
        )
    }
}

@Serializable
data class ProfilerConfig(
    val jfrConfig: JFRConfig = JFRConfig(),
    val asyncProfilerConfig: AsyncProfilerConfig = AsyncProfilerConfig(),
    val file: String = "\$PROJECT_DIR/profile.jfr",
    val conversionConfig: ConversionConfig = ConversionConfig()
) {

    fun getJFRFile(project: Project): Path {
        return fileNameToPath(project, file)
    }

    /** null if not existing */
    fun getJFRVirtualFile(project: Project): VirtualFile? {
        return VirtualFileManager.getInstance().refreshAndFindFileByNioPath(getJFRFile(project))
    }

    private fun write(configFile: Path) = Files.writeString(configFile, jsonFormat.encodeToString(this))

    companion object {

        const val PROFILE_CONFIG_FILE = "\$PROJECT_DIR/.profileconfig.json"

        fun fileNameToPath(project: Project, fileName: String): Path {
            return Path.of(fileName.replace("\$PROJECT_DIR", project.guessProjectDir()!!.path))
        }

        @OptIn(ExperimentalSerializationApi::class)
        fun get(project: Project): ProfilerConfig {
            val configFile = fileNameToPath(project, PROFILE_CONFIG_FILE)
            return if (Files.exists(configFile)) {
                configFile.inputStream().use {
                    jsonFormat.decodeFromStream<ProfilerConfig>(it)
                }.also { config ->
                    if (Files.readString(configFile) != jsonFormat.encodeToString(config)) {
                        config.write(configFile)
                    }
                }
            } else {
                ProfilerConfig().also { config ->
                    config.write(configFile)
                }
            }
        }
    }
}

val Project.profilerConfig
    get() = ProfilerConfig.get(this)

val Project.jfrFile
    get() = profilerConfig.getJFRFile(this)

val Project.jfrVirtualFile
    get() = profilerConfig.getJFRVirtualFile(this)

val Project.jfrSettingsFile
    get() = ProfilerConfig.fileNameToPath(this, profilerConfig.jfrConfig.settings)
