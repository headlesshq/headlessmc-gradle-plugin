pluginManagement {
    // Uses the plugins from this repository. In your own project use the published version instead, see the README.
    includeBuild("../..")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        // HeadlessMc 3.0.0-RC9-SNAPSHOT, see the README of the hmc-plugin example
        mavenLocal()
    }
}

rootProject.name = "multi-project"

// a HeadlessMc plugin and a mod that is launched with HeadlessMc + the plugin
include("platform-plugin", "mod")
