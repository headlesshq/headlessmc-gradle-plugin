package io.github.headlesshq.headlessmc.gradle;

/**
 * How HeadlessMc is run. HeadlessMc requires Java 25, which Gradle itself might not run on,
 * so HeadlessMc is always started as a separate process.
 */
public enum HmcDistribution {
    /**
     * Uses {@link #JVM} if a Java toolchain for {@link HeadlessMcExtension#getJavaVersion()} is available
     * and the HeadlessMc jar can be resolved, otherwise {@link #NATIVE}.
     */
    AUTO,
    /**
     * Runs the {@code headlessmc-application} runner jar from Maven Central on a Java toolchain.
     */
    JVM,
    /**
     * Runs the native executable from the GitHub release, which needs no Java installation.
     */
    NATIVE
}
