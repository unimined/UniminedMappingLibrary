import java.net.URI

plugins {
    kotlin("jvm")
    `maven-publish`
}

kotlin {
    jvmToolchain(8)
}

java {
    withSourcesJar()
}

dependencies {
    api(project(":"))

    api(libs.asm)
    api(libs.asm.tree)
    api(libs.asm.commons)

    implementation(libs.asm.analysis)
    implementation(libs.kotlin.metadata.jvm)

    // source remapper PSI engine — implementation (not api) so
    // consumers can swap in a lighter engine (javac-only…) or
    // exclude it entirely if they never touch SourceRemapper
    implementation(libs.kotlin.compiler.embeddable)

    implementation(libs.slf4j.api)

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlin.coroutines.tests)
    testImplementation(libs.slf4j.simple)
    // path-2 e2e — decompile → transform → linemap
    testImplementation("org.vineflower:vineflower:1.10.1")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "unimined-mapping-remapper"
        }
    }
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

// tests run on a newer JVM (vineflower, records) — main toolchain stays 8
tasks.withType<Test>().configureEach {
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(21))
    })
    // perf harness knobs: -Dasmremap.perf.jar/.mappings, -Ptest.heap=4g
    providers.systemProperty("asmremap.perf.jar").orNull?.let { systemProperty("asmremap.perf.jar", it) }
    providers.systemProperty("asmremap.perf.mappings").orNull?.let { systemProperty("asmremap.perf.mappings", it) }
    providers.gradleProperty("test.heap").orNull?.let { maxHeapSize = it }
    providers.gradleProperty("test.jfr").orNull?.let {
        jvmArgs("-XX:StartFlightRecording=duration=300s,filename=$it")
    }
}
