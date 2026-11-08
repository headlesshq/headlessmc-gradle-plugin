plugins {
    // puts the plugin classes on the classpath of this script, the subprojects apply it
    id("io.github.headlesshq.headlessmc-gradle-plugin") apply false
}

// Both projects share one HeadlessMc in <root>/run/headlessmc.
// Directories in the root project are fine, HeadlessMc only may not leave the build.
subprojects {
    plugins.withId("io.github.headlesshq.headlessmc-gradle-plugin") {
        extensions.configure<io.github.headlesshq.headlessmc.gradle.HeadlessMcExtension> {
            directory = rootProject.layout.projectDirectory.dir("run/headlessmc")
            // a local HeadlessMc build, see the README of the hmc-plugin example
            providers.gradleProperty("headlessmcMutableJar").orNull?.let {
                launcherConfig.put("hmc.plugins.mutable-jar", rootProject.file(it).absolutePath)
            }
        }

        providers.gradleProperty("headlessmcJar").orNull?.let {
            dependencies.add("headlessmcRuntime", files(rootProject.file(it)))
        }
    }
}
