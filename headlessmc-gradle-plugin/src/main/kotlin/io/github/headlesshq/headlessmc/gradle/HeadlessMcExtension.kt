package io.github.headlesshq.headlessmc.gradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.jvm.toolchain.JavaLauncher

/**
 * The `headlessmc` extension, configures how HeadlessMc is run for all HeadlessMc tasks of the project.
 *
 * Everything HeadlessMc writes is placed in [directory], by default `run/headlessmc`.
 * Directories outside the build are rejected, so HeadlessMc never uses e.g. `~/.minecraft`.
 */
abstract class HeadlessMcExtension {
    /**
     * The HeadlessMc version, e.g. `3.0.0-RC8`.
     */
    abstract val version: Property<String>

    /**
     * The Maven group of HeadlessMc, `io.github.headlesshq` (Maven Central) by default.
     * Other groups, e.g. `com.github.headlesshq.headlessmc` for [JitPack](https://jitpack.io) builds,
     * have no native executables.
     */
    abstract val group: Property<String>

    /**
     * The name of an included build of HeadlessMc to run instead of a published HeadlessMc,
     * e.g. a git submodule of the headlessmc repository added with `includeBuild('headlessmc')` in the settings.
     * Its `headlessmc-application` is built with `quarkusBuild` before every HeadlessMc task
     * and runs on the JVM, dependencies on `io.github.headlesshq` modules are substituted by its projects.
     */
    abstract val includedBuild: Property<String>

    /**
     * How HeadlessMc is run, [HmcDistribution.AUTO] by default.
     */
    abstract val distribution: Property<HmcDistribution>

    /**
     * The Java version HeadlessMc runs on with [HmcDistribution.JVM], 25 by default.
     */
    abstract val javaVersion: Property<Int>

    /**
     * The Java launcher HeadlessMc runs on with [HmcDistribution.JVM].
     * Defaults to the toolchain for [javaVersion], if one is available.
     */
    abstract val javaLauncher: Property<JavaLauncher>

    /**
     * JVM arguments for HeadlessMc itself (not the game) with [HmcDistribution.JVM].
     */
    abstract val jvmArgs: ListProperty<String>

    /**
     * The directory all HeadlessMc files are placed in, by default `<project>/run/headlessmc`.
     */
    abstract val directory: DirectoryProperty

    /**
     * The HeadlessMc config, data, state and cache directory (`hmc.files.location`).
     * Contains config.properties, logs, accounts, downloaded Java versions, plugins and servers.
     * Defaults to `<directory>/launcher`.
     */
    abstract val launcherDirectory: DirectoryProperty

    /**
     * The Minecraft directory with versions, libraries and assets (`hmc.files.mc`).
     * Defaults to `<directory>/mc`.
     */
    abstract val mcDirectory: DirectoryProperty

    /**
     * The directory native executables are downloaded to, defaults to `<directory>/native`.
     */
    abstract val nativeDirectory: DirectoryProperty

    /**
     * A native HeadlessMc executable to use instead of downloading it, e.g. a local build.
     * Setting it implies [HmcDistribution.NATIVE].
     */
    abstract val executable: RegularFileProperty

    /**
     * The url native executables are downloaded from, `<url>/<version>/<executable>`.
     */
    abstract val nativeDownloadUrl: Property<String>

    /**
     * The SHA-256 hash of the native executable for this machine.
     * If not set, the hash is taken from the GitHub release.
     */
    abstract val nativeSha256: Property<String>

    /**
     * HeadlessMc configuration for all tasks, e.g. `hmc.assets.dummy=true`.
     * The `hmc.files.*` keys are managed by the plugin and cannot be set.
     */
    abstract val launcherConfig: MapProperty<String, String>

    /**
     * If HeadlessMc may use JLine, false by default, since JLine does not work well inside Gradle.
     */
    abstract val jline: Property<Boolean>

    /**
     * If the HeadlessMc plugins directory is synchronized with the `headlessmcPlugins` configuration
     * before every task. True by default.
     */
    abstract val managePlugins: Property<Boolean>
}
