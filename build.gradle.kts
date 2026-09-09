plugins {
    id("fabric-loom") version "1.17.20"
    `java-library`
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

repositories {
    maven("https://api.modrinth.com/maven") { name = "Modrinth" }
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")

    // Provides javax.annotation.Nullable etc. — NeoForge bundles this transitively,
    // Fabric doesn't, and upstream Placebo/Apotheosis source uses it throughout.
    include("com.google.code.findbugs:jsr305:3.0.2")
    implementation("com.google.code.findbugs:jsr305:3.0.2")

    // Guava's own optional annotation deps (@LazyInit, @WeakOuter) — referenced by
    // AbstractBiMap.java (a near-verbatim copy of Guava's internal AbstractBiMap).
    // Not bundled transitively on Fabric the way NeoForge's classpath includes them.
    compileOnly("com.google.errorprone:error_prone_annotations:2.31.0")
    compileOnly("com.google.j2objc:j2objc-annotations:3.0.0")

    // dev-time-only reference to the item-transfer/capability replacement; add
    // as a real dependency once the `cap` package port confirms it's needed.
    implementation("maven.modrinth:cardinal-components-api:d78LiKJ8")
}

loom {
    accessWidenerPath.set(file("src/main/resources/placebo.accesswidener"))
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand(mapOf("version" to project.version))
    }
}
