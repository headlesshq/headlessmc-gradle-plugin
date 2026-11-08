import io.github.headlesshq.headlessmc.gradle.HeadlessMcRunTask
import io.github.headlesshq.headlessmc.gradle.HmcDistribution

plugins {
    id("io.github.headlesshq.headlessmc-gradle-plugin")
}

headlessmc {
    // HeadlessMc needs Java 25. By default (AUTO) it runs on a Java 25 toolchain,
    // or the native executable from the GitHub release if none is available.
    // NATIVE always uses the native executable, so no Java 25 and no Maven repository are needed.
    distribution = HmcDistribution.NATIVE
    // the executable is downloaded to run/headlessmc/native/<version>/ and verified with the SHA-256 hash
    // of the GitHub release, or with this hash:
    // nativeSha256 = "..."
    // a mirror of the GitHub releases:
    // nativeDownloadUrl = "https://example.com/headlessmc/releases"
    // or a local executable, e.g. your own build:
    // executable = layout.projectDirectory.file("headlessmc-launcher-linux-x64")
}

tasks.register<HeadlessMcRunTask>("runNative") {
    minecraftVersion = "1.21.4"
    headless = true
    offline = true
    dummyAssets = true
}
