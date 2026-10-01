import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI

val jeffreyVersion = "0.13.32"
val jeffreyJarUrl = "https://github.com/petrbouda/jeffrey/releases/download/v$jeffreyVersion/microscope.jar"
val jeffreyJarDest = layout.projectDirectory.file("src/main/resources/jeffrey/microscope.jar")

group = "me.bechberger"
description = "A profiler plugin for Java based on JFR and Firefox Profiler"

class ProjectInfo {
    val longName = "Java Profiler and JFR Profile Viewer"
    val website = "https://github.com/parttimenerd/intellij-profiler-plugin"
    val scm = "git@github.com:parttimenerd/intellij-profiler-plugin.git"
}

configurations.all {
    resolutionStrategy.cacheDynamicVersionsFor(0, "hours")
    resolutionStrategy.cacheChangingModulesFor(0, "hours")
}

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"

    id("com.gradleup.shadow") version "8.3.11"

    `maven-publish`
    application

    id("org.jetbrains.intellij.platform") version "2.6.0"
    id("org.jetbrains.changelog") version "2.2.0"
    id("org.jetbrains.qodana") version "2024.3.4"
    id("com.github.ben-manes.versions") version "0.50.0"
    idea
    signing
}

apply {
    plugin("com.gradleup.shadow")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

fun properties(key: String) = project.findProperty(key).toString()

repositories {
    mavenLocal()
    mavenCentral()
    gradlePluginPortal()
    maven {
        url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        mavenContent { snapshotsOnly() }
    }
    maven {
        url = uri("https://maven.pkg.github.com/parttimenerd/jfrtofp-server")
        credentials {
            username = providers.environmentVariable("GITHUB_ACTOR").orNull ?: "token"
            password = providers.environmentVariable("GITHUB_TOKEN").orNull
                ?: providers.gradleProperty("gpr.key").orNull ?: ""
        }
    }
    intellijPlatform {
        defaultRepositories()
    }
}

intellijPlatform {
    pluginConfiguration {
        name = properties("pluginName")
        version = properties("pluginVersion")

        ideaVersion {
            sinceBuild = properties("pluginSinceBuild")
        }

        description = projectDir.resolve("README.md").readText().lines().run {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"
            if (!containsAll(listOf(start, end))) {
                throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
            }
            subList(indexOf(start) + 1, indexOf(end))
        }.joinToString("\n").run { markdownToHTML(this) }

        changeNotes = provider {
            with(changelog) {
                renderItem(
                    getOrNull(properties("pluginVersion"))
                        ?: runCatching { getLatest() }.getOrElse { getUnreleased() },
                    Changelog.OutputType.HTML
                )
            }
        }
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

changelog {
    version.set(properties("pluginVersion"))
    groups.set(emptyList())
    repositoryUrl.set(properties("pluginRepositoryUrl"))
}

qodana {
    resultsPath.set(projectDir.resolve("build/results/qodana").canonicalPath)
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(properties("platformVersion"))
        bundledPlugin("com.intellij.java")
        bundledPlugin("com.intellij.mcpServer")
        pluginVerifier()
        zipSigner()
    }

    implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.4.20"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.4.20")

    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.4.20")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("me.bechberger:jfrtofp-server:0.0.13") {
        isChanging = true
    }
    implementation("me.bechberger:jfrtofp:0.0.9") {
        isChanging = true
    }
    implementation("me.bechberger:ap-loader-all:4.5-13")
}

tasks.test {
    useJUnitPlatform()
    dependsOn("prepareSandbox")
}

application {
    mainClass.set("me.bechberger.jfrtofp.MainKt")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "21"
    targetCompatibility = "21"
}

tasks.register<Copy>("copyHooks") {
    from("bin/pre-commit")
    into(".git/hooks")
}

val includeJeffrey = project.findProperty("includeJeffrey")?.toString()?.toBoolean() ?: true

tasks.register("downloadJeffrey") {
    description = "Downloads the Jeffrey microscope.jar viewer (v$jeffreyVersion) into src/main/resources/jeffrey/"
    onlyIf {
        if (!includeJeffrey) return@onlyIf false
        val dest = jeffreyJarDest.asFile
        !dest.exists() || (System.currentTimeMillis() - dest.lastModified() > 86_400_000L)
    }
    doLast {
        val dest = jeffreyJarDest.asFile
        dest.parentFile.mkdirs()
        logger.lifecycle("Downloading Jeffrey $jeffreyVersion → ${dest.absolutePath}")
        URI(jeffreyJarUrl).toURL().openStream().use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
    }
}

tasks.named<ProcessResources>("processResources") {
    dependsOn("downloadJeffrey")
    if (!includeJeffrey) {
        exclude("jeffrey/**")
    }
}

tasks {
    buildSearchableOptions {
        enabled = false
    }

    wrapper {
        gradleVersion = properties("gradleVersion")
    }
}

tasks.findByName("build")?.dependsOn(tasks.findByName("copyHooks") as Any)

publishing {
    repositories {
        maven {
            name = "ossrh"
            url = uri("https://s01.oss.sonatype.org/content/repositories/releases/")
        }
    }
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "intellij-profiler-plugin"
            pom {
                name = "IntelliJ Profiler Plugin"
                description = project.description
                url = "https://github.com/parttimenerd/intellij-profiler-plugin"
                version = properties("pluginVersion")
                licenses {
                    license {
                        name = "MIT"
                        url = "https://opensource.org/license/MIT/"
                    }
                }
                developers {
                    developer {
                        name = "Johannes Bechberger"
                        email = "me@mostlynerdless.de"
                    }
                }
                scm {
                    val projectInfo = ProjectInfo()
                    url = properties("pluginRepositoryUrl")
                    connection = projectInfo.scm
                    developerConnection = projectInfo.scm
                }
            }
        }
    }
}

signing {
    sign(publishing.publications["mavenJava"])
}

repositories {
    maven {
        url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    }
}
