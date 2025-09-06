@file:Suppress("LongLine")

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.10"
    id("org.jetbrains.intellij.platform") version "2.9.0"
    id("com.github.ben-manes.versions") version "0.52.0"
}

repositories {
    mavenCentral()
    gradlePluginPortal()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdeaCommunity("2025.1.5.1")
        bundledPlugin("org.jetbrains.kotlin")
        bundledPlugin("com.intellij.java")

        pluginVerifier()
    }
}

// Configure IntelliJ Platform Gradle Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild.set(providers.gradleProperty("pluginSinceBuild"))
        }
    }

    pluginVerification {
        ides {
            create(IntelliJPlatformType.IntellijIdeaCommunity, "2025.1.5.1")
//            recommended()
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
}

//java {
//    sourceCompatibility = JavaVersion.VERSION_21
//    targetCompatibility = JavaVersion.VERSION_21
//}

// Set the JVM language level used to build the project.
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        languageVersion = KotlinVersion.KOTLIN_2_2
        apiVersion = KotlinVersion.KOTLIN_2_2
    }

    jvmToolchain(21)
}

tasks {
//    withType<JavaCompile> {
//        sourceCompatibility = "21"
//        targetCompatibility = "21"
//    }
    withType<KotlinCompile> {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
        compilerOptions.languageVersion.set(KotlinVersion.KOTLIN_2_2)
        compilerOptions.apiVersion.set(KotlinVersion.KOTLIN_2_2)
    }

    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }

    withType<DependencyUpdatesTask> {
        rejectVersionIf {
            isStable(currentVersion) && !isStable(candidate.version)
        }

        checkForGradleUpdate = true
        outputDir = "build/dependencyUpdates"
        reportfileName = "report"
    }
}

fun isStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val latestKeyword = listOf("SNAPSHOT").any { version.uppercase().contains(it) }
    val regex = "^[\\d,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return isStable || latestKeyword
}
