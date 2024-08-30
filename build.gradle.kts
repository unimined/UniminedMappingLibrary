import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import xyz.wagyourtail.commons.gradle.shadow.ShadowJar
import java.net.URI

plugins {
    val kotlinVersion: String by System.getProperties()
    kotlin("multiplatform") version kotlinVersion
    `maven-publish`
    kotlin("plugin.serialization") version kotlinVersion
    id("xyz.wagyourtail.commons-gradle") version "1.0.0-SNAPSHOT"
}

allprojects {
    apply(plugin = "base")

    version = if (project.hasProperty("version_snapshot")) project.properties["version"] as String + "-SNAPSHOT" else project.properties["version"] as String
    group = project.properties["group"] as String

    base {
        archivesName.set(project.properties["archives_base_name"] as String)
    }

    repositories {
        mavenCentral()
        maven("https://maven.wagyourtail.xyz/snapshots")
    }
}

val kotlinVersion: String by System.getProperties()
val javaVersion: String by System.getProperties()

kotlin {
    jvmToolchain(8)
    jvm {
        withJava()
        compilations.all {
            kotlinOptions {
                freeCompilerArgs = listOf("-Xjsr305=strict")
            }
        }
    }
    js {
        browser {
            useCommonJs()
        }
        nodejs {
            useCommonJs()
        }
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api("xyz.wagyourtail.commons:commons-kt:1.0.0-SNAPSHOT")
                api("io.github.oshai:kotlin-logging:6.0.1")
                api("com.squareup.okio:okio:3.7.0")
                api("com.sschr15.annotations:jb-annotations-kmp:24.1.0")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0-RC2")
                api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0-RC2")
            }
        }
        val jvmMain by getting {
            dependencies {
                // apache compress
                api("org.apache.commons:commons-compress:1.26.1")

                // asm
                api("org.ow2.asm:asm:9.6")
                api("org.ow2.asm:asm-tree:9.6")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(npm("jszip", "3.10.1"))
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }
}

publishing {
    repositories {
        maven {
            name = "WagYourMaven"
            url = if (project.hasProperty("version_snapshot")) {
                URI.create("https://maven.wagyourtail.xyz/snapshots/")
            } else {
                URI.create("https://maven.wagyourtail.xyz/releases/")
            }
            credentials {
                username = project.findProperty("mvn.user") as String? ?: System.getenv("USERNAME")
                password = project.findProperty("mvn.key") as String? ?: System.getenv("TOKEN")
            }
        }
    }
}