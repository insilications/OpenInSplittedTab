@file:Suppress("LongLine")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

//buildscript {
//    repositories {
//        mavenCentral()
//        gradlePluginPortal()
//    }
//    dependencies {
//        classpath("com.github.ben-manes:gradle-versions-plugin:+")
//    }
//}

apply(plugin = "com.github.ben-manes.versions")

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.10"
    id("org.jetbrains.intellij.platform") version "2.9.0"
    id("com.github.ben-manes.versions") version "0.52.0"
    id("org.jetbrains.changelog") version "2.4.0"
}

//allprojects {
//    repositories {
//        mavenCentral()
//        gradlePluginPortal()
//    }
//
//    tasks.withType<DependencyUpdatesTask> {
//        rejectVersionIf {
//            isNonStable(candidate.version) && !isNonStable(currentVersion)
//        }
//
//        checkForGradleUpdate = true
//        outputDir = "build/dependencyUpdates"
//        reportfileName = "report"
//    }
//}

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
//        plugins(providers.gradleProperty("platformPlugins").map { it.split(',') })

        intellijIdeaCommunity("2025.1.5")
        bundledPlugin("org.jetbrains.kotlin")
        bundledPlugin("com.intellij.java")
//        androidStudio("2025.1.4.4")
        plugin("org.jetbrains.android:251.27812.49")
//        bundledPlugin("org.jetbrains.android")
//        local("/aot/stuff/dev/android-studio/")
    }
}

// Configure IntelliJ Platform Gradle Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild.set(providers.gradleProperty("pluginSinceBuild"))
//            untilBuild.set(providers.gradleProperty("pluginUntilBuild"))
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
}

// Set the JVM language level used to build the project.
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget("21")
    }
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }

    publishPlugin {
        dependsOn(patchChangelog)
    }
}

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return isStable.not()
}