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

val shadowJar = tasks.register("shadowJar", ShadowJar::class) {
    description = "Generates a jar with dependency classes included"
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
