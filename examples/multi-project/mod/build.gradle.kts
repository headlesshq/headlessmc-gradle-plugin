import io.github.headlesshq.headlessmc.gradle.HeadlessMcExec
import io.github.headlesshq.headlessmc.gradle.HeadlessMcRunTask

plugins {
    java
    id("io.github.headlesshq.headlessmc-gradle-plugin")
}

dependencies {
    // installs the plugin built by :platform-plugin into the HeadlessMc of this project
    headlessmcPlugins(project(":platform-plugin"))
}

tasks.register<HeadlessMcExec>("greet") {
    args("greet")
}

tasks.register<HeadlessMcRunTask>("runClient") {
    from(tasks.jar)
    into(modsDirectory)
    minecraftVersion = "1.21.4"
    headless = true
    offline = true
    dummyAssets = true
    // runs the plugin's command before launching
    command("greet")
}
