import xyz.wagyourtail.commons.gradle.shadow.ShadowJar

plugins {
    kotlin("jvm")
}

val cliktVersion: String by project.properties

dependencies {
    implementation(project(":"))

    implementation("com.github.ajalt.clikt:clikt:$cliktVersion")

    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("org.slf4j:slf4j-simple:2.0.10")

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
