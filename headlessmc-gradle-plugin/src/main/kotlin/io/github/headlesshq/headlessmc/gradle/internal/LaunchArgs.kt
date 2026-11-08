package io.github.headlesshq.headlessmc.gradle.internal

import io.github.headlesshq.headlessmc.gradle.HmcPlatform
import java.util.Locale

/**
 * Builds the arguments for the HeadlessMc `launch` command.
 *
 * @property server if a server is launched instead of the client.
 * @property platform the platform, e.g. fabric.
 * @property minecraftVersion the Minecraft version, e.g. 1.21.4.
 * @property loaderVersion the version of the platform, e.g. the fabric loader version.
 * @property headless if the client is launched headless.
 * @property offline if an offline account is used.
 * @property acceptEula if the server EULA is accepted.
 * @property jvmArgs arguments for the game JVM.
 * @property gameArgs arguments for the game.
 * @property joinServer the address of a server the client joins.
 * @property resolution the resolution, e.g. 800x600.
 * @property retries how often launching is retried.
 * @property patchers the patchers to use.
 * @property extraArgs more arguments, added before the version.
 */
data class LaunchArgs(
    val server: Boolean = false,
    val platform: String = HmcPlatform.VANILLA,
    val minecraftVersion: String,
    val loaderVersion: String? = null,
    val headless: Boolean = false,
    val offline: Boolean = false,
    val acceptEula: Boolean = false,
    val jvmArgs: List<String> = emptyList(),
    val gameArgs: List<String> = emptyList(),
    val joinServer: String? = null,
    val resolution: String? = null,
    val retries: Int? = null,
    val patchers: List<String> = emptyList(),
    val extraArgs: List<String> = emptyList(),
) {
    private val normalizedPlatform: String get() = platform.lowercase(Locale.ENGLISH)

    /**
     * @return the version arguments, e.g. `[server, fabric, 1.21.4, 0.16.14]`, used by launch and mod add.
     */
    fun versionArgs(): List<String> = buildList {
        if (server) {
            add("server")
        }

        if (normalizedPlatform != HmcPlatform.VANILLA || loaderVersion != null) {
            add(normalizedPlatform)
        }

        add(minecraftVersion)
        loaderVersion?.let { add(it) }
    }

    /**
     * @return the version arguments for `mod add`. HeadlessMc can only resolve a version with a build
     * (loader version) once it is installed, so clients leave the build out, the game directory is the same.
     * Servers keep it, because HeadlessMc uses a different directory for every server version.
     */
    fun modVersionArgs(): List<String> =
        if (server || loaderVersion == null) versionArgs() else copy(loaderVersion = null).versionArgs()

    /**
     * @return the arguments for HeadlessMc, starting with `launch`.
     */
    fun toArgs(): List<String> = buildList {
        add("launch")
        if (headless) add("--headless")
        if (offline) add("--offline")
        if (acceptEula) add("--eula-accept")
        if (jvmArgs.isNotEmpty()) addAll(listOf("--jvm", Args.join(jvmArgs)))
        if (gameArgs.isNotEmpty()) addAll(listOf("--game", Args.join(gameArgs)))
        joinServer?.let { addAll(listOf("--server", it)) }
        resolution?.let { addAll(listOf("--resolution", it)) }
        retries?.let { addAll(listOf("--retries", it.toString())) }
        if (patchers.isNotEmpty()) addAll(listOf("--patchers", patchers.joinToString(",")))
        addAll(extraArgs)
        addAll(versionArgs())
    }

    /**
     * @return the name of the directory HeadlessMc uses for this server, relative to the server directory.
     * Mirrors `GameDirService`, which uses `"server-" + versionArg.toString("-")`.
     */
    fun serverDirectoryName(): String {
        val args = versionArgs().toMutableList()
        if (!server) {
            args.add(0, "server")
        }

        if (normalizedPlatform == HmcPlatform.VANILLA && args.size > 1 && args[1] == normalizedPlatform) {
            args.removeAt(1)
        }

        return "server-" + args.joinToString("-")
    }
}
