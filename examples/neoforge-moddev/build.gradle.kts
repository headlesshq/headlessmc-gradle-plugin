import io.github.headlesshq.headlessmc.gradle.HeadlessMcRunTask
import io.github.headlesshq.headlessmc.gradle.HmcPlatform

plugins {
    id("net.neoforged.moddev") version "2.0.148"
    id("io.github.headlesshq.headlessmc")
}

version = "1.0.0"
group = "com.example"

base {
    archivesName = "examplemod"
}

repositories {
    mavenCentral()
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

val mcVersion = "1.21.1"
val neoVersion = "21.1.265"
// HeadlessMc names NeoForge builds by their last part, see ./gradlew headlessmc --args="version list --remote neoforge 1.21.1"
val hmcNeoBuild = neoVersion.substringAfterLast('.')

neoForge {
    version = neoVersion
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand("version" to project.version)
    }
}

// The jar task produces the release jar, no remapping is needed with ModDevGradle.
tasks.register<HeadlessMcRunTask>("runHeadlessMc") {
    from(tasks.jar)
    into(modsDirectory)

    minecraftVersion = mcVersion
    platform = HmcPlatform.NEOFORGE
    loaderVersion = hmcNeoBuild
}

tasks.register<HeadlessMcRunTask>("runHeadlessMcTest") {
    from(tasks.jar)
    into(modsDirectory)
    modrinth("mc-runtime-test")

    minecraftVersion = mcVersion
    platform = HmcPlatform.NEOFORGE
    loaderVersion = hmcNeoBuild
    headless = true
    offline = true
    dummyAssets = true
    ciOptions()
}
