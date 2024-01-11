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
            args("--name=test")
        }
    }
    js {
        browser()
        nodejs()
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("io.github.oshai:kotlin-logging:6.0.1")
                implementation("com.github.ajalt.clikt:clikt:$cliktVersion")
                implementation("com.squareup.okio:okio:3.7.0")
                implementation("com.sschr15.annotations:jb-annotations-kmp:24.1.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmMain by getting {
            dependencies {
                // slf4j logback
                runtimeOnly("ch.qos.logback:logback-classic:1.4.14") {
                    exclude(group= "org.slf4j", module= "slf4j-api")
                }
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