plugins {
    id("fabric-loom") version "1.14.10"
    java
}

group = "com.palordersoftworks"
version = rootProject.version

loom {
    mixin {
        useLegacyMixinAp.set(true)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
}

val minecraftVersion = "1.21.11"
val yarnMappings = "1.21.11+build.4"
val loaderVersion = "0.18.1"
val fabricApiVersion = "0.141.1+1.21.11"

repositories {
    exclusiveContent {
        forRepository { mavenCentral() }
        filter { includeGroup("org.yaml") }
    }
    mavenCentral()
}

val copyDefaultConfigs = tasks.register<Copy>("copyDefaultConfigs") {
    from(rootProject.layout.projectDirectory.dir("core/src/main/resources"))
    include(
        "config.yml",
        "spawners_settings.yml",
        "item_spawners_settings.yml",
        "item_prices.yml",
        "spawners_data.yml",
    )
    into(layout.buildDirectory.dir("generated/smartspawnerResources/defaults"))
}

sourceSets {
    main {
        resources {
            srcDir(layout.buildDirectory.dir("generated/smartspawnerResources"))
        }
    }
}

val fabricPermissionsApiVersion = "0.6.1"

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:$yarnMappings:v2")
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    include(modImplementation("me.lucko:fabric-permissions-api:$fabricPermissionsApiVersion")!!)

    implementation("org.yaml:snakeyaml:2.2")
    include(implementation("org.yaml:snakeyaml:2.2")!!)


    compileOnly("org.projectlombok:lombok:1.18.44")
    annotationProcessor("org.projectlombok:lombok:1.18.44")
}

tasks.named("processResources") {
    dependsOn(copyDefaultConfigs)
}

tasks.named<Jar>("sourcesJar") {
    dependsOn(copyDefaultConfigs)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filesMatching("fabric.mod.json") {
        expand(props)
    }
}

tasks.remapJar {
    archiveBaseName.set("SmartSpawner-Fabric")
    archiveClassifier.set("")
}

tasks.build {
    dependsOn(tasks.named("remapJar"))
}
