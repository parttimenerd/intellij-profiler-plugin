package me.bechberger.jfrplugin.runner

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.impl.DefaultJavaProgramRunner
import com.intellij.execution.process.CapturingProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.RunConfigurationWithSuppressedDefaultRunAction
import com.intellij.execution.target.TargetEnvironmentAwareRunProfileState
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import me.bechberger.jfrplugin.settings.JFRPluginSettings
import org.jetbrains.concurrency.Promise

class JFRProgramRunner : DefaultJavaProgramRunner() {

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        return try {
            (
                executorId == JFRExecutor.EXECUTOR_ID &&
                    profile !is RunConfigurationWithSuppressedDefaultRunAction &&
                    profile is RunConfigurationBase<*>
                )
        } catch (_: Exception) {
            false
        }
    }

    @Throws(ExecutionException::class)
    override fun patch(
        javaParameters: JavaParameters,
        settings: RunnerSettings?,
        runProfile: RunProfile,
        beforeExecution: Boolean
    ) {
        super.patch(javaParameters, settings, runProfile, beforeExecution)
        if (beforeExecution) {
            val vmParametersList = javaParameters.vmParametersList
            vmParametersList.add("-XX:+FlightRecorder")
            val project = (runProfile as RunConfigurationBase<*>).project
            val sets = JFRPluginSettings.getInstance(project)
            vmParametersList.add("-XX:StartFlightRecording=filename=${sets.jfrFile},${sets.additionalJFRConfig}")
        }
    }

    @Throws(ExecutionException::class)
    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        val descriptor = super.doExecute(state, environment)
        workWithDescriptor(descriptor, environment.project)
        return descriptor
    }

    override fun doExecuteAsync(
        state: TargetEnvironmentAwareRunProfileState,
        env: ExecutionEnvironment
    ): Promise<RunContentDescriptor?> {
        return super.doExecuteAsync(state, env)
            .then { descriptor -> workWithDescriptor(descriptor, env.project); return@then descriptor }
    }

    fun workWithDescriptor(descriptor: RunContentDescriptor?, project: Project) {
        if (descriptor != null) {
            val processHandler = descriptor.processHandler
            processHandler?.addProcessListener(object : CapturingProcessAdapter() {
                override fun processTerminated(event: ProcessEvent) {
                    super.processTerminated(event)

                    ApplicationManager.getApplication().invokeLater {
                        // FileEditorManager.getInstance(project)
                        //    .openFile(JFRPluginSettings.getJFRFile(project)!!, true)
                        OpenFileDescriptor(project, JFRPluginSettings.getJFRFile(project)!!).navigate(true)
                    }
                }
            })
        }
    }

    override fun getRunnerId() = RUNNER_ID

    companion object {
        const val RUNNER_ID = "Java Profile Runner"
    }
}
