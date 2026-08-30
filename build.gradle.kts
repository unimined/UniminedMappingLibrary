@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform") version libs.versions.kotlin.asProvider()
    kotlin("plugin.serialization") version libs.versions.kotlin.asProvider()
    alias(libs.plugins.commons)
    alias(libs.plugins.kotlinx.atomicfu)
    alias(libs.plugins.resources)
    `maven-publish`
}

allprojects {
    pluginManager.apply("base")
    pluginManager.apply("xyz.wagyourtail.commons-gradle")

    group = project.property("group").toString()
    base.archivesName = project.property("archives_base_name").toString()

    commons.autoVersion(defaultSnapshot = true)

    repositories {
        mavenCentral()
        maven("https://maven.wagyourtail.xyz/releases")
        maven("https://maven.wagyourtail.xyz/snapshots")
    }
}

kotlin {
    jvmToolchain(8)
    jvm {
        compilerOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
        }
    }
    js {
        useCommonJs()
        browser {
        }
        nodejs()
        binaries.executable()
    }
    wasmJs {
        browser {
        }
        nodejs()
        binaries.executable()
    }

    linuxX64()
    linuxArm64()

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain {
            dependencies {
                api(libs.commons.kt)
                api(libs.kotlin.logging)
                api(libs.okio)
                api(libs.jetbrains.annotations.kmp)
                api(libs.kotlin.coroutines)
                api(libs.kotlin.serialization.json)
                api(libs.kmp.zip)
                api(libs.kmp.zip.okio)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation(libs.kotlin.coroutines.tests)
                implementation(libs.resources)
            }
        }
        val nonJvmMain = create("nonJvmMain") {
            dependsOn(commonMain.get())
        }
        jvmMain {
            dependencies {
                api(libs.bundles.asm)

                api(libs.slf4j.api)
                api(libs.slf4j.simple)
            }
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
            }
        }

        webMain {
            dependsOn(nonJvmMain)
        }
        jsTest {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }

        nativeMain {
            dependsOn(nonJvmMain)
        }
    }
}

tasks.getByName("allTests") {
    dependsOn(tasks.getByName("kotlinUpgradeYarnLock"))
}

publishing {
    repositories {
        val cred = Action<PasswordCredentials> {
            username = project.findProperty("mvn.user") as String? ?: System.getenv("USERNAME")
            password = project.findProperty("mvn.key") as String? ?: System.getenv("TOKEN")
        }
        if (project.hasProperty("version_release")) {
            maven("https://maven.wagyourtail.xyz/releases/") {
                name = "WagYourMaven-Releases"
                credentials(cred)
            }
        }
        if (project.hasProperty("version_snapshot")) {
            maven("https://maven.wagyourtail.xyz/snapshots/") {
                name = "WagYourMaven-Snapshots"
                credentials(cred)
            }
        }
    }
}
