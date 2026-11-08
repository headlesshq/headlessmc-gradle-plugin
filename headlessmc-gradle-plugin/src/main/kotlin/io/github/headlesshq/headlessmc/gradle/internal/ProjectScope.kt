package io.github.headlesshq.headlessmc.gradle.internal

import org.gradle.api.GradleException
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

/**
 * Makes sure that HeadlessMc only places its files inside the Gradle build.
 * HeadlessMc would otherwise use the users home directory, e.g. ~/.minecraft, ~/.config/headlessmc.
 *
 * @param root the directory everything has to be placed in, the root directory of the build.
 */
class ProjectScope(root: File) {
    /**
     * The normalized root directory.
     */
    val root: Path = normalize(root.toPath())

    /**
     * @param name a name for the directory, used in the error message.
     * @param directory the directory to check.
     * @return the normalized absolute directory.
     * @throws GradleException if the directory is not inside the [root].
     */
    fun check(name: String, directory: File): Path {
        val path = normalize(directory.toPath())
        if (!path.startsWith(root) || path == root) {
            throw GradleException(
                "HeadlessMc may only use directories inside the build at $root, but $name is $path." +
                    " Choose a directory inside the project.",
            )
        }

        return path
    }

    companion object {
        /**
         * Configuration keys that would move HeadlessMc files somewhere else.
         * They are managed by the Gradle plugin.
         */
        @JvmField
        val MANAGED_KEYS: Set<String> = setOf(
            "hmc.files.location",
            "hmc.files.mc",
            "hmc.files.game",
            // legacy names of hmc.files.mc and hmc.files.game
            "hmc.mcdir",
            "hmc.gamedir",
        )

        /**
         * @param source where the configuration comes from, for the error message.
         * @param config HeadlessMc configuration given by the user.
         * @throws GradleException if the configuration contains a [managed key][MANAGED_KEYS].
         */
        @JvmStatic
        fun checkConfig(source: String, config: Map<String, String>) {
            config.keys.forEach { checkKey(source, it) }
        }

        /**
         * @param source where the arguments come from, for the error message.
         * @param jvmArgs JVM arguments given by the user.
         * @throws GradleException if the arguments set a [managed key][MANAGED_KEYS] as system property.
         */
        @JvmStatic
        fun checkJvmArgs(source: String, jvmArgs: Iterable<String>) {
            jvmArgs.filter { it.startsWith("-D") }
                .forEach { checkKey(source, it.substring(2).substringBefore('=')) }
        }

        private fun checkKey(source: String, key: String) {
            if (key.trim().lowercase(Locale.ENGLISH) in MANAGED_KEYS) {
                throw GradleException(
                    "$source sets $key, which is managed by the HeadlessMc Gradle plugin," +
                        " so that HeadlessMc only places files inside the project." +
                        " Use the directory properties of the headlessmc extension or the task instead.",
                )
            }
        }

        /**
         * Absolute, normalized and with symlinks of the existing part of the path resolved,
         * so that a symlink inside the project cannot point outside of it.
         */
        @JvmStatic
        fun normalize(path: Path): Path {
            val absolute = path.toAbsolutePath().normalize()
            var existing: Path? = absolute
            while (existing != null && !Files.exists(existing)) {
                existing = existing.parent
            }

            if (existing == null) {
                return absolute
            }

            return try {
                existing.toRealPath().resolve(existing.relativize(absolute)).normalize()
            } catch (_: IOException) {
                absolute
            }
        }
    }
}
