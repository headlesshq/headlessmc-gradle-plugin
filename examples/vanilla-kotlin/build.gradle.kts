import io.github.headlesshq.headlessmc.gradle.HeadlessMcRunTask

plugins {
    id("io.github.headlesshq.headlessmc")
}

repositories {
    // HeadlessMc itself (io.github.headlesshq:headlessmc-application:<version>:runner) comes from Maven Central
    mavenCentral()
}

// Launches the vanilla client, headless, with an offline account and dummy assets.
// Everything HeadlessMc downloads ends up in run/headlessmc, never in ~/.minecraft.
tasks.register<HeadlessMcRunTask>("runVanilla") {
    minecraftVersion = "1.21.4"
    headless = true
    offline = true
    dummyAssets = true
    // pauseOnLostFocus:false, onboardAccessibility:false, narrator:0 in options.txt
    ciOptions()
    gameJvmArgs.addAll("-Xmx2G", "-Dsome.property=a value with spaces")
}

// Launches with a window and your Microsoft account, log in first with:
// ./gradlew headlessmc --args="login"
tasks.register<HeadlessMcRunTask>("runVanillaWindowed") {
    minecraftVersion = "1.21.4"
    resolution = "1280x720"
}
