package io.github.headlesshq.headlessmc.gradle

/**
 * How HeadlessMc is run. HeadlessMc requires Java 25, which Gradle itself might not run on,
 * so HeadlessMc is always started as a separate process.
 */
enum class HmcDistribution {
    /**
     * Uses [JVM] if a Java toolchain for [HeadlessMcExtension.javaVersion] is available
     * and the HeadlessMc jar can be resolved, otherwise [NATIVE].
     */
    AUTO,

    /**
     * Runs the `headlessmc-application` runner jar from Maven Central on a Java toolchain.
     */
    JVM,

    /**
     * Runs the native executable from the GitHub release, which needs no Java installation.
     */
    NATIVE,
}
