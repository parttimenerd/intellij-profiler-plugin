package me.bechberger.jfrplugin.runner

import com.intellij.execution.Executor
import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.project.Project
import me.bechberger.jfrplugin.config.additionalGradleTargets
import me.bechberger.jfrplugin.config.additionalMavenTargets
import org.jdom.Element
import java.nio.file.Files


/**
 * The class is used to extend the Run Configuration with my own profiling settings.
 */
abstract class BasePluginRunConfigurationExtension(private val name: String, private val executorId: String) :
    RunConfigurationExtension() {

    private fun mergeConfs(conf1: Map<String, String>, conf2: Map<String, String>): Map<String, String> {
        val mergedConfs = conf1.toMutableMap()
        conf2.forEach { (key, value) ->
            if (mergedConfs[key] == null) {
                mergedConfs[key] = value
            }
        }
        return mergedConfs
    }

    private fun mavenConfs(project: Project): Map<String, String> {
        return mergeConfs(project.additionalMavenTargets, defaultMavenConfs)
    }

    private fun gradleConfs(project: Project): Map<String, String> {
        return mergeConfs(project.additionalGradleTargets, defaultGradleConfs)
    }

    override fun readExternal(configuration: RunConfigurationBase<*>, element: Element) = Unit

    override fun writeExternal(configuration: RunConfigurationBase<*>, element: Element) = Unit

    override fun getEditorTitle(): String {
        return name
    }

    override fun isEnabledFor(configuration: RunConfigurationBase<*>, runnerSettings: RunnerSettings?): Boolean {
        return isApplicableFor(configuration)
    }

    override fun <T : RunConfigurationBase<*>?> updateJavaParameters(
        configuration: T & Any,
        params: JavaParameters,
        runnerSettings: RunnerSettings?,
    ) {
        throw UnsupportedOperationException("Not implemented")
    }

    override fun <T : RunConfigurationBase<*>?> updateJavaParameters(
        configuration: T & Any,
        params: JavaParameters,
        runnerSettings: RunnerSettings?,
        executor: Executor,
    ) {
        if (executor.id == executorId) {
            val vmArgs = computeVmParameters(configuration.project)
            val paramString = vmArgs.joinToString(" ") { "\"$it\"" }
            val paramList = params.programParametersList
            when (configuration.getType().displayName) {
                "Maven" -> {
                    val matchingConfs = mavenConfs(configuration.project).filter { (key, _) ->
                        paramList.parameters.any {
                            it.startsWith(
                                key
                            )
                        }
                    }
                    matchingConfs.forEach { (_, value) -> paramList.add("$value=$paramString") }
                    if (matchingConfs.isEmpty()) {
                        params.env.getOrDefault("MAVEN_OPTS", "").let {
                            params.env["MAVEN_OPTS"] = "$it $paramString"
                        }
                    }
                }

                "Gradle" -> {
                    val matchingConfs = gradleConfs(configuration.project).filter { (key, _) ->
                        (configuration as ExternalSystemRunConfiguration).settings.taskNames.any {
                            it.startsWith(
                                key
                            )
                        }
                    }
                    matchingConfs.forEach { (_, value) -> paramList.add("$value=$paramString") }
                    if (matchingConfs.isEmpty()) {
                        // Inject via a temp init script so the args reach forked test JVMs,
                        // not just the Gradle daemon (GRADLE_OPTS only reaches the daemon).
                        val initScript = writeGradleInitScript(vmArgs)
                        paramList.add("--init-script")
                        paramList.add(initScript)
                    }
                }

                else -> {
                    params.vmParametersList.addAll(vmArgs)
                }
            }
        }
    }

    /** Writes a Gradle init script that injects [vmArgs] into every Test task's jvmArgs.
     *  Returns the absolute path of the written file. */
    private fun writeGradleInitScript(vmArgs: List<String>): String {
        val escapedArgs = vmArgs.joinToString(", ") { arg ->
            // Escape backslashes and single quotes for a Groovy string literal
            "'" + arg.replace("\\", "\\\\").replace("'", "\\'") + "'"
        }
        val script = """
            allprojects {
                tasks.withType(Test).configureEach {
                    jvmArgs($escapedArgs)
                }
            }
        """.trimIndent()
        val tmpFile = Files.createTempFile("jfr-profiling-", ".gradle")
        tmpFile.toFile().deleteOnExit()
        Files.writeString(tmpFile, script)
        return tmpFile.toAbsolutePath().toString()
    }

    override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean {
        return configuration.getType().displayName == "Maven" ||
                configuration.getType().displayName == "Gradle" ||
                listOf("Java", "Kotlin", "Application", "JAR Application", "JUnit", "TestNG")
            .any { configuration.type.displayName.startsWith(it) }
    }

    abstract fun computeVmParameters(project: Project): List<String>

    companion object {
        val defaultMavenConfs = mapOf("quarkus:" to "-Djvm.args", "spring-boot:" to "-Dspring-boot.run.jvmArguments")
        val defaultGradleConfs = mapOf("quarkus" to "-Djvm.args")
    }
}
