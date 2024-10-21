pluginManagement {
    repositories {
        maven("https://maven.wagyourtail.xyz/releases")
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

include("cli")

rootProject.name = "unimined-mapping-library"

