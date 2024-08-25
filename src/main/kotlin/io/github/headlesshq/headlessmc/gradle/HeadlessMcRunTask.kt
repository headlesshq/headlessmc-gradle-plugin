package io.github.headlesshq.headlessmc.gradle

import io.github.headlesshq.headlessmc.gradle.internal.GradleLauncher
import io.github.headlesshq.headlessmc.gradle.internal.GradleLauncherBuilder
import me.earth.headlessmc.api.config.Property
import me.earth.headlessmc.jline.JLineProperties
import me.earth.headlessmc.launcher.java.Java
import me.earth.headlessmc.launcher.modlauncher.Modlauncher
import me.earth.headlessmc.launcher.version.Version
import me.earth.headlessmc.launcher.version.family.FamilyUtil
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Optional
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.work.DisableCachingByDefault
import java.util.*
import java.util.stream.Collectors.joining
import kotlin.collections.HashMap

/**
 * Runs Minecraft with the HeadlessMc launcher.
 *
 * @author 3arthqu4ke
 */
@DisableCachingByDefault(because = "Gradle would require more information to cache this task")
abstract class HeadlessMcRunTask: Copy() {
    /**
     * The Minecraft version to use.
     * E.g. 1.21.1
     */
    @Input
    var version: String? = null

    /**
     * The [Modlauncher] to use.
     */
    @Input
    var modlauncher: Modlauncher = Modlauncher.FABRIC

    /**
     * The version of the modloader to use, for Lexforge e.g. 52.0.4
     */
    @Input
    @Optional
    var modLoaderVersion: String? = null

    /**
     * If you want to download the hmc-specifics mod.
     */
    @Input
    var specifics: Boolean = false

    /**
     * If you want to download the mc-runtime-test mod.
     */
    @Input
    var mcRuntimeTest: Boolean = false

    /**
     * If you want HeadlessMc to launch the game with the java versions defined in its config.
     * Otherwise, the Java executable of the configured in the JavaToolChain of the project will be used.
     */
    @Input
    var useAnyJava: Boolean = false

    /**
     * Generally JLine does not work from the IntelliJ console.
     * If false sets the [JLineProperties.ENABLED] SystemProperty to false for the spawned Minecraft process.
     */
    @Input
    var jline: Boolean = false

    /**
     * If you want to run Minecraft in headless mode through HeadlessMc's lwjgl instrumentation.
     */
    @Input
    var lwjgl: Boolean = false

    /**
     * If you want HeadlessMc to use dummy assets.
     * Useful when running headless, lets you run the game without downloading megabytes of assets.
     */
    @Input
    var dummyAssets: Boolean = false

    /**
     * If you want HeadlessMc to use dummy assets.
     * Useful when running headless, lets you run the game without downloading megabytes of assets.
     */
    @Input
    var rethrowLaunchExceptions: Boolean = true

    /**
     * Allows you to override properties from HeadlessMcs config.
     */
    @get:Input
    @get:Optional
    var launcherConfig: MutableMap<String, String> = HashMap()

    /**
     * Commands in HeadlessMc to execute before launching.
     */
    @get:Input
    @get:Optional
    var commands: MutableList<String> = ArrayList() // would use ListProperty but no += operator :(

    /**
     * Allows you to override the command HeadlessMc is going to be using.
     */
    @Input
    @Optional
    var command: String? = null

    /**
     * The directory the HeadlessMc configuration files, logs and plugins will be stored in.
     */
    @Optional
    @InputDirectory
    val launcherDirectory: DirectoryProperty = project.objects.directoryProperty().convention(
        mkdir(getDefaultLauncherDirectory())
    )

    /**
     * The directory assets, libraries and version jsons will be stored in.
     */
    @Optional
    @InputDirectory
    val mcDirectory: DirectoryProperty = project.objects.directoryProperty().convention(
        mkdir(getDefaultLauncherDirectory().dir("mc"))
    )

    /**
     * The directory the game will run in.
     */
    @Optional
    @InputDirectory
    val gameDirectory: DirectoryProperty = project.objects.directoryProperty().convention(
        mkdir(getDefaultLauncherDirectory().dir("run"))
    )

    /**
     * The mods directory inside the [gameDirectory].
     */
    @get:Internal
    val modsDirectory: Provider<Directory> = project.provider { gameDirectory.dir("mods").get() }

    @TaskAction
    override fun copy() {
        val launcher = GradleLauncherBuilder(this).buildDefault() as GradleLauncher

        val versionToLaunch = getVersionToLaunch(launcher)
        setupJavaService(launcher, versionToLaunch)
        super.copy()

        if (specifics) {
            launcher.commandLine.commandContext.execute("specifics ${versionToLaunch.name} hmc-specifics")
        }

        if (mcRuntimeTest) {
            launcher.commandLine.commandContext.execute("specifics ${versionToLaunch.name} mc-runtime-test")
        }

        for (command in commands) {
            launcher.commandLine.commandContext.execute(command)
        }

        var launchCommand = "launch ${versionToLaunch.name}"
        val command = this.command
        if (command != null) {
            launchCommand = command
        } else if (lwjgl) {
            launchCommand += " -lwjgl"
        }

        launcher.commandLine.commandContext.execute(launchCommand)
    }

    private fun setupJavaService(launcher: GradleLauncher, version: Version) {
        if (!useAnyJava) {
            launcher.javaService.ensureInitialized()
            launcher.javaService.clear()
            val toolChainService = project.extensions.getByType(JavaToolchainService::class.java)
            val javaVersion = version.java ?: version.parent!!.java
            val javaLauncher = toolChainService.launcherFor {
                it.languageVersion.set(JavaLanguageVersion.of(javaVersion))
            }

            if (!javaLauncher.isPresent) {
                throw IllegalStateException(version.name + " requires Java " + javaVersion +
                        "! Failed to find a JavaLauncher for " + javaVersion
                        + " on your ToolChainService. You could set useAnyJava to true")
            }

            project.logger.info("Found JavaLauncher ${javaLauncher.get().executablePath.asFile}")
            launcher.javaService.add(Java(javaLauncher.get().executablePath.asFile.absolutePath, javaVersion))
        }
    }

    private fun getVersionToLaunch(launcher: GradleLauncher): Version {
        if (launcher.versionService.stream().noneMatch { it.name.contains(version!!) }) {
            launcher.commandLine.commandContext.execute("download $version")
            if (launcher.versionService.stream().noneMatch { it.name.contains(version!!) }) {
                throw IllegalStateException("Executed 'download $version' but there is still no vanilla version")
            }
        }

        var versionToLaunch = findVersionToLaunch(launcher)
        if (versionToLaunch == null) {
            var installCommand = modlauncher.officialName + " $version"
            if (modLoaderVersion != null) {
                installCommand += " --uid $modLoaderVersion"
            }

            launcher.commandLine.commandContext.execute(installCommand)
            versionToLaunch = findVersionToLaunch(launcher)
            if (versionToLaunch == null) {
                throw IllegalStateException(
                    "Executed '${modlauncher.officialName} $version' but version has not been installed. Available: "
                        + launcher.versionService.stream().map { v -> v.name }.collect(joining(", ")))
            }
        }

        return versionToLaunch
    }

    private fun findVersionToLaunch(launcher: GradleLauncher): Version? {
        return launcher.versionService.stream().filter {
            (it.name.contains(version!!) || FamilyUtil.getOldestParent(it).name.contains(version!!))
                    && it.name.lowercase(Locale.ENGLISH).contains(modlauncher.officialName)
                    && (modLoaderVersion == null || (it.name.contains(modLoaderVersion!!))) }
            .findFirst().orElse(null)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun mkdir(dir: Provider<Directory>): Provider<Directory> {
        return project.provider { mkdir(dir.get()).get() }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun mkdir(dir: Directory): Provider<Directory> {
        return project.provider {
            if (!dir.asFile.exists()) {
                dir.asFile.mkdirs()
            }

            dir
        }
    }

    private fun getDefaultLauncherDirectory(): Directory {
        return project.layout.projectDirectory
            .dir("run")
            .dir("task-${name.replace(Regex("[^a-zA-Z0-9-_]"), "")}")
    }

    /* Would like clear out the modsFolder, but idk
    /**
     * The mods folder in the [gameDirectory] gets cleared before launch,
     * but it can be populated with mods from these directories before launch.
    */
    @Optional
    @InputFiles
    val persistentModDirectories: ConfigurableFileCollection = project.objects.fileCollection().from(
        getDefaultLauncherDirectory().dir("persistent").asFile
    )

    private fun prepareMods(launcher: GradleLauncher) {
        val mods = launcher.gameDir.getDir("mods")
        launcher.gameDir.delete(mods)
        mods.mkdirs()
        project.copy { copySpec ->
            persistentModDirectories.forEach { dir ->
                if (dir.isDirectory) {
                    copySpec.from(dir)
                } else {
                    if (dir.exists()) {
                        project.logger.error("$dir in persistentModDirectories is not a directory!")
                    } else {
                        dir.mkdirs()
                    }
                }
            }

            copySpec.into(mods)
        }
    } */

}