package io.github.headlesshq.headlessmc.gradle.internal

import java.util.Properties

/**
 * The version of this Gradle plugin and the default HeadlessMc version.
 */
object PluginInfo {
    private val properties: Properties = Properties().apply {
        val stream = PluginInfo::class.java.getResourceAsStream("/io/github/headlesshq/headlessmc/gradle/version.properties")
            ?: throw IllegalStateException("version.properties is missing")
        stream.use { load(it) }
    }

    /**
     * The version of this Gradle plugin.
     */
    @JvmStatic
    val version: String get() = properties.getProperty("version")

    /**
     * The HeadlessMc version used by default.
     */
    @JvmStatic
    val headlessMcVersion: String get() = properties.getProperty("headlessmc")
}
