import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI

val jeffreyVersion = "0.14.7"
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
            password = providers.environmentVariable("GPR_TOKEN").orNull
                ?: providers.environmentVariable("GITHUB_TOKEN").orNull
                ?: providers.gradleProperty("gpr.key").orNull ?: ""
        }
        mavenContent {
            includeGroup("me.bechberger")
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

    pluginVerification {
        ides {
            ide(IntelliJPlatformType.IntellijIdeaCommunity, "2025.2.6")
        }
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
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("me.bechberger:jfrtofp-server:0.0.14") {
        isChanging = true
        isTransitive = false
    }
    implementation("me.bechberger:jfrtofp:0.0.14") {
        isChanging = true
        isTransitive = false
    }
    implementation("me.bechberger:condensed-data:0.1.4") {
        isChanging = true
        isTransitive = false
    }
    // Transitive deps of jfrtofp-server (declared explicitly because GitHub Packages requires read:packages)
    implementation("io.javalin:javalin:7.2.3") { isTransitive = false }
    implementation("org.eclipse.jetty:jetty-server:12.1.12") { isTransitive = false }
    implementation("org.eclipse.jetty:jetty-http:12.1.12") { isTransitive = false }
    implementation("org.eclipse.jetty:jetty-io:12.1.12") { isTransitive = false }
    implementation("org.eclipse.jetty:jetty-util:12.1.12") { isTransitive = false }
    implementation("org.eclipse.jetty:jetty-security:12.1.12") { isTransitive = false }
    implementation("org.eclipse.jetty:jetty-session:12.1.12") { isTransitive = false }
    implementation("org.eclipse.jetty:jetty-xml:12.1.12") { isTransitive = false }
    implementation("org.eclipse.jetty:jetty-plus:12.1.12") { isTransitive = false }
    implementation("org.eclipse.jetty:jetty-jndi:12.1.12") { isTransitive = false }
    implementation("org.eclipse.jetty:jetty-annotations:12.1.12") { isTransitive = false }
    implementation("org.eclipse.jetty.ee:jetty-ee-webapp:12.1.12") { isTransitive = false }
    implementation("org.eclipse.jetty.ee10:jetty-ee10-servlet:12.1.12") { isTransitive = false }
    implementation("org.eclipse.jetty.ee10:jetty-ee10-webapp:12.1.12") { isTransitive = false }
    implementation("org.eclipse.jetty.ee10:jetty-ee10-annotations:12.1.12") { isTransitive = false }
    implementation("org.eclipse.jetty.ee10:jetty-ee10-plus:12.1.12") { isTransitive = false }
    implementation("org.eclipse.jetty.ee10.websocket:jetty-ee10-websocket-jetty-server:12.1.12") { isTransitive = false }
    implementation("org.eclipse.jetty.ee10.websocket:jetty-ee10-websocket-servlet:12.1.12") { isTransitive = false }
    implementation("org.eclipse.jetty.websocket:jetty-websocket-core-common:12.1.12") { isTransitive = false }
    implementation("org.eclipse.jetty.websocket:jetty-websocket-core-server:12.1.12") { isTransitive = false }
    implementation("org.eclipse.jetty.websocket:jetty-websocket-jetty-api:12.1.12") { isTransitive = false }
    implementation("org.eclipse.jetty.websocket:jetty-websocket-jetty-common:12.1.12") { isTransitive = false }
    implementation("org.eclipse.jetty.websocket:jetty-websocket-jetty-server:12.1.12") { isTransitive = false }
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.0") { isTransitive = false }
    implementation("com.fasterxml.jackson.core:jackson-core:2.19.0") { isTransitive = false }
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.19.0") { isTransitive = false }
    implementation("org.slf4j:slf4j-api:2.0.18") { isTransitive = false }
    implementation("org.slf4j:slf4j-simple:2.0.17") { isTransitive = false }
    implementation("jakarta.servlet:jakarta.servlet-api:6.0.0") { isTransitive = false }
    implementation("jakarta.annotation:jakarta.annotation-api:2.1.1") { isTransitive = false }
    implementation("jakarta.enterprise:jakarta.enterprise.cdi-api:4.0.1") { isTransitive = false }
    implementation("jakarta.inject:jakarta.inject-api:2.0.1") { isTransitive = false }
    implementation("jakarta.interceptor:jakarta.interceptor-api:2.1.0") { isTransitive = false }
    implementation("jakarta.transaction:jakarta.transaction-api:2.0.1") { isTransitive = false }
    implementation("info.picocli:picocli:4.7.7") { isTransitive = false }
    implementation("org.ow2.asm:asm:9.10.1") { isTransitive = false }
    implementation("org.ow2.asm:asm-commons:9.10.1") { isTransitive = false }
    implementation("org.ow2.asm:asm-tree:9.10.1") { isTransitive = false }
    implementation("io.jafar:jafar-parser:0.27.0") { isTransitive = false }
    implementation("org.openjdk.jmc:flightrecorder.writer:9.1.2")
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
