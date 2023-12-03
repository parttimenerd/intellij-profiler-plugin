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
            val paramString = computeVmParameters(configuration.project).joinToString(" ") { "\"$it\"" }
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
                        params.env.getOrDefault("GRADLE_OPTS", "").let {
                            params.env["GRADLE_OPTS"] = "$it $paramString"
                        }
                    }
                }

                else -> {
                    params.vmParametersList.addAll(computeVmParameters(configuration.project))
                }
            }
        }
    }

    override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean {
        return configuration.getType().displayName == "Maven" ||
                (configuration.getType().displayName == "Gradle" &&
                        (configuration as ExternalSystemRunConfiguration).settings.taskNames.any { task ->
            gradleConfs(
                configuration.project
            ).keys.any { task.startsWith(it) }
        }) || listOf("Java", "Kotlin", "Application", "JAR Application")
            .any { configuration.type.displayName.startsWith(it) }
    }

    abstract fun computeVmParameters(project: Project): List<String>

    companion object {
        val defaultMavenConfs = mapOf("quarkus:" to "-Djvm.args", "spring-boot:" to "-Dspring-boot.run.jvmArguments")
        val defaultGradleConfs = mapOf("quarkus" to "-Djvm.args")
    }
}
