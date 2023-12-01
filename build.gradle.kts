import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "me.bechberger"
description = "A profiler plugin for Java based on JFR and Firefox Profiler"

inner class ProjectInfo {
    val longName = "Java Profiler and JFR Profile Viewer"
    val website = "https://github.com/parttimenerd/intellij-profiler-plugin"
    val scm = "git@github.com:parttimenerd/intellij-profiler-plugin.git"
}

configurations.all {
    resolutionStrategy.cacheDynamicVersionsFor(0, "hours")
    resolutionStrategy.cacheChangingModulesFor(0, "hours")
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
    gradlePluginPortal()
}

plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    id("org.jetbrains.kotlin.jvm") version "1.8.0"
    kotlin("plugin.serialization") version "1.8.0"

    id("com.github.johnrengelman.shadow") version "8.1.1"

    `maven-publish`

    // Apply the application plugin to add support for building a CLI application in Java.
    application
    // Gradle IntelliJ Plugin
    id("org.jetbrains.intellij") version "1.16.1"
    // Gradle Changelog Plugin
    id("org.jetbrains.changelog") version "2.2.0"
    // Gradle Qodana Plugin
    id("org.jetbrains.qodana") version "2023.2.1"
    id("com.github.ben-manes.versions") version "0.50.0"
    idea
}

apply { plugin("com.github.johnrengelman.shadow") }

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

fun properties(key: String) = project.findProperty(key).toString()

// Configure Gradle IntelliJ Plugin - read more: https://github.com/JetBrains/gradle-intellij-plugin
intellij {
    pluginName.set(properties("pluginName"))
    version.set(properties("platformVersion"))
    type.set(properties("platformType"))

    // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file.
    plugins.set(properties("platformPlugins").split(',').map(String::trim).filter(String::isNotEmpty))
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    version.set(properties("pluginVersion"))
    groups.set(emptyList())
    repositoryUrl.set(properties("pluginRepositoryUrl"))
}

// Configure Gradle Qodana Plugin - read more: https://github.com/JetBrains/gradle-qodana-plugin
qodana {
    resultsPath.set(projectDir.resolve("build/results/qodana").canonicalPath)
    //reportPath.set(projectDir.resolve("build/reports/qodana").canonicalPath)
    //saveReport.set(true)
    //showReport.set(System.getenv("QODANA_SHOW_REPORT")?.toBoolean() ?: false)
}

dependencies {
    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.9.21"))

    // Use the Kotlin JDK 8 standard library.
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.21")

    // This dependency is used by the application.
    implementation("org.junit.jupiter:junit-jupiter:5.10.1")

    // Use the Kotlin JUnit integration.
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.9.21")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("me.bechberger:jfrtofp-server:0.0.2-SNAPSHOT") {
        isChanging = true
    }
    implementation("me.bechberger:jfrtofp:0.0.2-SNAPSHOT") {
        isChanging = true
    }
    implementation("me.bechberger:ap-loader-all:2.9-7")
}

tasks.test {
    useJUnitPlatform()
}

application {
    // Define the main class for the application.
    mainClass.set("me.bechberger.jfrtofp.MainKt")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.register<Copy>("copyHooks") {
    from("bin/pre-commit")
    into(".git/hooks")
}

tasks {
    buildSearchableOptions {
        enabled = false
    }
}

tasks.findByName("build")?.dependsOn(tasks.findByName("copyHooks"))

tasks {
    // Set the JVM compatibility versions
    properties("javaVersion").let {
        withType<JavaCompile> {
            sourceCompatibility = it
            targetCompatibility = it
        }
        withType<KotlinCompile> {
            kotlinOptions.jvmTarget = it
        }
    }

    wrapper {
        gradleVersion = properties("gradleVersion")
    }

    patchPluginXml {
        version.set(properties("pluginVersion"))
        sinceBuild.set(properties("pluginSinceBuild"))
        untilBuild.set(properties("pluginUntilBuild"))

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        pluginDescription.set(
            projectDir.resolve("README.md").readText().lines().run {
                val start = "<!-- Plugin description -->"
                val end = "<!-- Plugin description end -->"

                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end))
            }.joinToString("\n").run { markdownToHTML(this) }
        )

        // Get the latest available change notes from the changelog file
        changeNotes.set(
            provider {
                with(changelog) {
                    renderItem(
                        getOrNull(properties("pluginVersion"))
                            ?: runCatching { getLatest() }.getOrElse { getUnreleased() },
                        Changelog.OutputType.HTML
                    )
                }
            }
        )
    }

    // Configure UI tests plugin
    // Read more: https://github.com/JetBrains/intellij-ui-test-robot
    runIdeForUiTests {
        systemProperty("robot-server.port", "8082")
        systemProperty("ide.mac.message.dialogs.as.sheets", "false")
        systemProperty("jb.privacy.policy.text", "<!--999.999-->")
        systemProperty("jb.consents.confirmation.enabled", "false")
    }

    /*publishPlugin {
        dependsOn("patchChangelog")
        token.set(properties("PUBLISH_TOKEN"))
        // pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels.set(listOf(properties("pluginVersion").split('-').getOrElse(1) { "default" }.split('.').first()))
    }*/
}

repositories {
    maven {
        url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    }
}
