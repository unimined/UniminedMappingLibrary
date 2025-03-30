import java.net.URI

plugins {
    kotlin("multiplatform") version libs.versions.kotlin.asProvider()
    kotlin("plugin.serialization") version libs.versions.kotlin.asProvider()
    alias(libs.plugins.commons)
    alias(libs.plugins.kotlinx.atomicfu)
    `maven-publish`
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
        maven("https://maven.wagyourtail.xyz/releases")
        maven("https://maven.wagyourtail.xyz/snapshots")
    }
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
    }
    js {
        browser {
            useCommonJs()

            testTask {
                enabled = false
            }
        }
        nodejs {
            useCommonJs()
        }
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(libs.commons.kt)
                api(libs.kotlin.logging)
                api(libs.okio)
                api(libs.jetbrains.annotations.kmp)
                api(libs.kotlin.coroutines)
                api(libs.kotlin.serialization.json)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation(libs.kotlin.coroutines.tests)
            }
        }
        val jvmMain by getting {
            dependencies {
                api(libs.appache.commons.compress)

                api(libs.asm)
                api(libs.asm.tree)

                api(libs.slf4j.api)
                api(libs.slf4j.simple)
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
                implementation(npm("jszip", libs.versions.jszip.get()))
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