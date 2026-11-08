package io.github.headlesshq.headlessmc.gradle.dev

import java.util.Properties

/**
 * The HeadlessMc plugins are built against by default.
 * Until HeadlessMc 3.0.0-RC9 is released, this is a snapshot, e.g. a local build or an included build.
 */
internal object Defaults {
    private val properties: Properties = Properties().apply {
        val stream = Defaults::class.java.getResourceAsStream("headlessmc.properties")
            ?: throw IllegalStateException("headlessmc.properties is missing")
        stream.use { load(it) }
    }

    /**
     * The Maven group of the default HeadlessMc.
     */
    @JvmField
    val GROUP: String = properties.getProperty("group")

    /**
     * The version of the default HeadlessMc.
     */
    @JvmField
    val VERSION: String = properties.getProperty("version")

    /**
     * The HeadlessMc versions plugins built against the default HeadlessMc are compatible with.
     */
    @JvmField
    val COMPATIBLE: String = properties.getProperty("compatible")
}
