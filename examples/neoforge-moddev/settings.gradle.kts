pluginManagement {
    // Uses the plugin from this repository. In your own project use the published version instead, see the README.
    includeBuild("../..")
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.neoforged.net/releases")
    }
}

rootProject.name = "neoforge-moddev"
