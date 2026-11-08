package io.github.headlesshq.headlessmc.gradle

/**
 * Names of the platforms (mod loaders and servers) HeadlessMc supports out of the box.
 * Platforms are plain strings, so that platforms added by HeadlessMc plugins, e.g. quilt, can be used, too.
 */
object HmcPlatform {
    const val VANILLA: String = "vanilla"
    const val FABRIC: String = "fabric"
    const val FORGE: String = "forge"
    const val NEOFORGE: String = "neoforge"

    /**
     * Server only.
     */
    const val PAPER: String = "paper"

    /**
     * Server only.
     */
    const val PURPUR: String = "purpur"
}
