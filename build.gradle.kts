import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import java.net.URI

plugins {
    val kotlinVersion: String by System.getProperties()
    kotlin("multiplatform") version kotlinVersion
    `maven-publish`
    kotlin("plugin.serialization") version kotlinVersion
}

version = if (project.hasProperty("version_snapshot")) project.properties["version"] as String + "-SNAPSHOT" else project.properties["version"] as String
group = project.properties["group"] as String

base {
    archivesName.set(project.properties["archives_base_name"] as String)
}

val kotlinVersion: String by System.getProperties()
val javaVersion: String by System.getProperties()

val cliktVersion: String by project.properties

val main = "xyz.wagyourtail.unimined.mapping.MainKt"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(8)
    jvm {
        withJava()
        compilations.all {
            kotlinOptions {
                freeCompilerArgs = listOf("-Xjsr305=strict")
            }
        }
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        mainRun {
            mainClass.set(main)
            args(
                "-i", "preProp-intermediary-yarn-1-stubs-0641d60.umf",
                "-pns", "official",
                "-p", "client.jar",
                "export", "umf", "propagated.umf"
            )
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
                implementation("io.github.oshai:kotlin-logging:6.0.1")
                implementation("com.squareup.okio:okio:3.7.0")
                implementation("com.sschr15.annotations:jb-annotations-kmp:24.1.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0-RC2")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
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
                implementation("com.github.ajalt.clikt:clikt:$cliktVersion")

                implementation("org.slf4j:slf4j-api:2.0.10")
                implementation("org.slf4j:slf4j-simple:2.0.10")

                // apache compress
                implementation("org.apache.commons:commons-compress:1.26.1")
                implementation("org.ow2.asm:asm:9.6")
                implementation("org.ow2.asm:asm-tree:9.6")
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