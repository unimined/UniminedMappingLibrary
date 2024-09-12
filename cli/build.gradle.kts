import xyz.wagyourtail.commons.gradle.shadow.ShadowJar

plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":"))

    implementation(libs.clikt)

    implementation(libs.slf4j.api)
    implementation(libs.slf4j.simple)

}

val shadowJar by tasks.registering(ShadowJar::class) {
    from(sourceSets.main.get().output)

    archiveBaseName.set(base.archivesName.get() + "-cli")
    archiveClassifier = "all"
    shadowContents.add(configurations.runtimeClasspath.get())

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("module-info.class")

    manifest {
        attributes(
            "Main-Class" to "xyz.wagyourtail.unimined.mapping.cli.MainKt"
        )
    }
}

tasks.assemble {
    dependsOn(shadowJar)
}
