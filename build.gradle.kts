import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    val kotlinVersion: String by System.getProperties()
    kotlin("multiplatform") version kotlinVersion
    `maven-publish`
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
            args("-i", "int.tiny", "-i", "yarn.tiny", "-pns", "official", "-p", "mc.jar", "-m", "official", "intermediary", "-m", "intermediary", "named", "-o", "tinyv2", "joined.tiny")
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
                implementation("org.apache.commons:commons-compress:1.25.0")
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