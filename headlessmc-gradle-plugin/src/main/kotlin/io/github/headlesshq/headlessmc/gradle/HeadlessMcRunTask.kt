package io.github.headlesshq.headlessmc.gradle

import groovy.lang.Closure
import io.github.headlesshq.headlessmc.gradle.internal.Args
import io.github.headlesshq.headlessmc.gradle.internal.LaunchArgs
import io.github.headlesshq.headlessmc.gradle.internal.MinecraftOptions
import io.github.headlesshq.headlessmc.gradle.internal.ProjectScope
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Transformer
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.CopySourceSpec
import org.gradle.api.file.CopySpec
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.nio.file.Path

/**
 * Launches Minecraft with HeadlessMc.
 *
 * The task works like a [org.gradle.api.tasks.Copy] task:
 * before launching, everything configured with [from] is copied [into]
 * the destination, by default the [mods directory][modsDirectory] of the game.
 * ```
 * tasks.register('runFabric', HeadlessMcRunTask) {
 *     from(remapJar)
 *     into(modsDirectory)
 *     minecraftVersion = '1.21.4'
 *     platform = 'fabric'
 *     headless = true
 *     offline = true
 * }
 * ```
 * The task launches `headlessmc launch [options] [server] [platform] <version> [loaderVersion]`,
 * after running [mod add][modrinth] for every Modrinth project and every [command][commands].
 */
@DisableCachingByDefault(because = "Launches Minecraft")
abstract class HeadlessMcRunTask : AbstractHeadlessMcTask(), CopySourceSpec {
    private val mods: CopySpec = project.copySpec()

    init {
        destinationDirectory.convention(modsDirectory)
    }

    /**
     * The Minecraft version to launch, e.g. `1.21.4`. Required, unless [launchArgs] is set.
     */
    @get:Input
    @get:Optional
    abstract val minecraftVersion: Property<String>

    /**
     * The platform (mod loader) to launch, see [HmcPlatform], vanilla by default.
     */
    @get:Input
    abstract val platform: Property<String>

    /**
     * The version of the platform, e.g. the Fabric loader version `0.16.14`. Latest by default.
     * Uses the build names of HeadlessMc, e.g. `265` for NeoForge `21.1.265`,
     * see `./gradlew headlessmc --args="version list --remote <platform> <version>"`.
     */
    @get:Input
    @get:Optional
    abstract val loaderVersion: Property<String>

    /**
     * If a server is launched instead of the client.
     */
    @get:Input
    abstract val server: Property<Boolean>

    /**
     * If the client runs headless, HeadlessMc patches LWJGL to not render anything (`--headless`).
     */
    @get:Input
    abstract val headless: Property<Boolean>

    /**
     * If an offline account is used (`--offline`), for CI. Offline accounts force headless mode
     * if no account has been logged in.
     */
    @get:Input
    abstract val offline: Property<Boolean>

    /**
     * If dummy assets are used instead of downloading the real assets (`hmc.assets.dummy`).
     */
    @get:Input
    abstract val dummyAssets: Property<Boolean>

    /**
     * If the server EULA is accepted automatically (`--eula-accept`).
     */
    @get:Input
    abstract val acceptEula: Property<Boolean>

    /**
     * Arguments for the JVM the game runs in (`--jvm`).
     */
    @get:Input
    abstract val gameJvmArgs: ListProperty<String>

    /**
     * Arguments for the game (`--game`).
     */
    @get:Input
    abstract val gameArgs: ListProperty<String>

    /**
     * A server the client joins after starting (`--server`).
     */
    @get:Input
    @get:Optional
    abstract val joinServer: Property<String>

    /**
     * The resolution of the client, e.g. `800x600` (`--resolution`).
     */
    @get:Input
    @get:Optional
    abstract val resolution: Property<String>

    /**
     * How often launching is retried (`--retries`).
     */
    @get:Input
    @get:Optional
    abstract val retries: Property<Int>

    /**
     * The patchers HeadlessMc uses (`--patchers`).
     */
    @get:Input
    abstract val patchers: ListProperty<String>

    /**
     * Additional arguments for the launch command.
     */
    @get:Input
    abstract val extraLaunchArgs: ListProperty<String>

    /**
     * Replaces the arguments of the launch, if set, e.g. `['launch', '--packwiz', 'pack', 'client']`.
     */
    @get:Input
    abstract val launchArgs: ListProperty<String>

    /**
     * Ids of Modrinth projects to add with `mod add mod <id> <platform> <version>` before launching,
     * e.g. `fabric-api` or `mc-runtime-test`. For servers with a [loaderVersion],
     * HeadlessMc needs the server to be installed already, e.g. by launching it once.
     */
    @get:Input
    abstract val modrinth: ListProperty<String>

    /**
     * HeadlessMc commands run before launching, each one a separate invocation,
     * e.g. `"java list"`. Quotes group arguments.
     */
    @get:Input
    abstract val commands: ListProperty<String>

    /**
     * Options merged into the `options.txt` of the game directory before launching,
     * e.g. `pauseOnLostFocus: false`.
     */
    @get:Input
    abstract val minecraftOptions: MapProperty<String, String>

    /**
     * If the destination directory is emptied before copying, so old versions of a mod do not remain.
     * True by default. To not delete anything by accident, the destination has to be a directory inside the
     * [runDirectory], [gameDirectory], [mcDirectory] or [launcherDirectory] then, otherwise the task fails
     * and you have to set this to false.
     */
    @get:Input
    abstract val cleanDestination: Property<Boolean>

    /**
     * The directory the files configured with [from] are copied into. Defaults to the [modsDirectory].
     */
    @get:Internal
    abstract val destinationDirectory: DirectoryProperty

    /**
     * All sources of [from], for task dependencies.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val source: ConfigurableFileCollection

    /**
     * The directory the game runs in. For the client this is the [gameDirectory],
     * for servers HeadlessMc uses a directory in `<launcherDirectory>/server`,
     * which is unknown (the provider is empty) for servers without [minecraftVersion].
     */
    @get:Internal
    val runDirectory: Provider<Directory>
        get() = server.flatMap { server ->
            if (server) {
                // empty without minecraftVersion, HeadlessMc picks the directory then
                launcherDirectory.dir("server").flatMap { dir ->
                    minecraftVersion.map { dir.dir(toLaunchArgs().serverDirectoryName()) }
                }
            } else {
                gameDirectory
            }
        }

    /**
     * The `mods` directory inside the [runDirectory].
     */
    @get:Internal
    val modsDirectory: Provider<Directory>
        get() = runDirectory.map { it.dir("mods") }

    /**
     * The `plugins` directory of a server, e.g. for Paper plugins. Not the HeadlessMc plugins.
     */
    @get:Internal
    val serverPluginsDirectory: Provider<Directory>
        get() = runDirectory.map { it.dir("plugins") }

    /**
     * The copy spec used to copy the files, e.g. to configure duplicate handling.
     */
    @get:Internal
    val copySpec: CopySpec
        get() = mods

    /**
     * Configures the [copySpec].
     *
     * @param action configures the spec.
     */
    fun copySpec(action: Action<in CopySpec>) {
        action.execute(mods)
    }

    override fun from(vararg sourcePaths: Any?): HeadlessMcRunTask {
        source.from(*sourcePaths)
        mods.from(*sourcePaths)
        return this
    }

    override fun from(sourcePath: Any, configureClosure: Closure<*>): HeadlessMcRunTask {
        source.from(sourcePath)
        mods.from(sourcePath, configureClosure)
        return this
    }

    override fun from(sourcePath: Any, configureAction: Action<in CopySpec>): HeadlessMcRunTask {
        source.from(sourcePath)
        mods.from(sourcePath, configureAction)
        return this
    }

    /**
     * Sets the [destinationDirectory], like [org.gradle.api.tasks.Copy.into].
     *
     * @param destination a directory, file or provider of one, e.g. `modsDirectory`.
     * @return this task.
     */
    fun into(destination: Any): HeadlessMcRunTask {
        val projectDir = project.projectDir
        if (destination is Provider<*>) {
            destinationDirectory.fileProvider(destination.map { toFile(projectDir, it) })
        } else {
            destinationDirectory.set(toFile(projectDir, destination))
        }

        return this
    }

    /**
     * Copies into a directory relative to the [destinationDirectory].
     *
     * @param destination the relative directory.
     * @param action configures the files to copy into it.
     * @return this task.
     */
    fun into(destination: Any, action: Action<in CopySpec>): HeadlessMcRunTask {
        mods.into(destination, action)
        return this
    }

    /**
     * @param includes Ant-style include patterns.
     * @return this task.
     */
    fun include(vararg includes: String): HeadlessMcRunTask {
        mods.include(*includes)
        return this
    }

    /**
     * @param excludes Ant-style exclude patterns.
     * @return this task.
     */
    fun exclude(vararg excludes: String): HeadlessMcRunTask {
        mods.exclude(*excludes)
        return this
    }

    /**
     * @param renamer renames copied files, returns null to keep the name.
     * @return this task.
     */
    fun rename(renamer: Transformer<String?, String>): HeadlessMcRunTask {
        mods.rename(renamer)
        return this
    }

    /**
     * @param sourceRegEx a regular expression for file names.
     * @param replaceWith the replacement, may contain groups like `$1`.
     * @return this task.
     */
    fun rename(sourceRegEx: String, replaceWith: String): HeadlessMcRunTask {
        mods.rename(sourceRegEx, replaceWith)
        return this
    }

    /**
     * @param action called for each copied file.
     * @return this task.
     */
    fun eachFile(action: Action<in FileCopyDetails>): HeadlessMcRunTask {
        mods.eachFile(action)
        return this
    }

    /**
     * @param strategy how duplicate files are handled.
     */
    fun setDuplicatesStrategy(strategy: DuplicatesStrategy) {
        mods.duplicatesStrategy = strategy
    }

    /**
     * Adds Modrinth projects, see [modrinth].
     *
     * @param ids the project ids.
     */
    fun modrinth(vararg ids: String) {
        modrinth.addAll(*ids)
    }

    /**
     * Adds a HeadlessMc command to run before launching, see [commands].
     *
     * @param command the command, e.g. `"java list"`.
     */
    fun command(command: String) {
        commands.add(command)
    }

    /**
     * Sets `pauseOnLostFocus:false`, `onboardAccessibility:false` and `narrator:0`,
     * recommended for running the client headless.
     */
    fun ciOptions() {
        minecraftOptions.put("pauseOnLostFocus", "false")
        minecraftOptions.put("onboardAccessibility", "false")
        minecraftOptions.put("narrator", "0")
    }

    /**
     * @return the launch arguments built from the properties of this task.
     */
    protected fun toLaunchArgs(): LaunchArgs = LaunchArgs(
        server = server.get(),
        platform = platform.get(),
        minecraftVersion = minecraftVersion.get(),
        loaderVersion = loaderVersion.orNull,
        headless = headless.get(),
        offline = offline.get(),
        acceptEula = acceptEula.get(),
        jvmArgs = gameJvmArgs.get(),
        gameArgs = gameArgs.get(),
        joinServer = joinServer.orNull,
        resolution = resolution.orNull,
        retries = retries.orNull,
        patchers = patchers.get(),
        extraArgs = extraLaunchArgs.get(),
    )

    override fun systemProperties(): MutableMap<String, String> {
        val properties = super.systemProperties()
        if (dummyAssets.get()) {
            properties.putIfAbsent("hmc.assets.dummy", "true")
        }

        if (server.get()) {
            // HeadlessMc ignores hmc.files.game for servers
            properties.remove("hmc.files.game")
        }

        return properties
    }

    @TaskAction
    fun launch() {
        if (!minecraftVersion.isPresent && launchArgs.get().isEmpty()) {
            throw GradleException("Set minecraftVersion on $path")
        }

        val scope = checkScope()
        if (!source.isEmpty && !destinationDirectory.isPresent) {
            throw GradleException(
                "Set destinationDirectory (into(...)) on $path: the default, the mods directory of the run " +
                    "directory, is unknown when using launchArgs without minecraftVersion and with server.",
            )
        }

        val destination = if (source.isEmpty) {
            null
        } else {
            scope.check("destinationDirectory", destinationDirectory.get().asFile).toFile()
        }

        val runDirectory = if (minecraftVersion.isPresent) runDirectory.get().asFile else null
        runDirectory?.let { scope.check("runDirectory", it) }

        destination?.let { checkCleanable(scope, it) }
        syncPlugins()
        destination?.let { copyFiles(it) }
        if (runDirectory != null && minecraftOptions.get().isNotEmpty()) {
            MinecraftOptions.merge(runDirectory.toPath().resolve("options.txt"), minecraftOptions.get())
        }

        val launchArgs = if (minecraftVersion.isPresent) toLaunchArgs() else null
        for (modrinth in modrinth.get()) {
            if (launchArgs == null) {
                throw GradleException("modrinth needs minecraftVersion to be set on $path")
            }

            runHeadlessMc(listOf("mod", "add", "mod", modrinth) + launchArgs.modVersionArgs())
        }

        for (command in commands.get()) {
            runHeadlessMc(Args.split(command))
        }

        val args = this.launchArgs.get()
        runHeadlessMc(args.ifEmpty { launchArgs!!.toArgs() })
    }

    /**
     * Refuses to empty directories that are not owned by HeadlessMc, e.g. `src`.
     */
    private fun checkCleanable(scope: ProjectScope, destination: File) {
        if (!cleanDestination.get() || !destination.isDirectory) {
            return
        }

        val target = scope.check("destinationDirectory", destination)
        val owned = listOfNotNull(
            launcherDirectory.orNull, mcDirectory.orNull, gameDirectory.orNull, runDirectory.orNull,
        ).map { scope.check("directory", it.asFile) }
        if (owned.none { target != it && target.startsWith(it) }) {
            throw GradleException(
                "Refusing to delete the contents of $target before copying on $path: it is not inside a " +
                    "directory of HeadlessMc (${owned.joinToString()}). " +
                    "Set cleanDestination = false to copy into it without deleting anything.",
            )
        }
    }

    private fun copyFiles(destination: File) {
        if (cleanDestination.get() && destination.isDirectory) {
            fileSystemOperations.delete { it.delete(*destination.listFiles().orEmpty()) }
        }

        fileSystemOperations.copy {
            it.into(destination)
            it.with(mods)
        }
    }

    private companion object {
        fun toFile(projectDir: File, value: Any): File {
            val file = when (value) {
                is Directory -> value.asFile
                is Path -> value.toFile()
                is File -> value
                else -> File(value.toString())
            }

            return if (file.isAbsolute) file else File(projectDir, file.path)
        }
    }
}
