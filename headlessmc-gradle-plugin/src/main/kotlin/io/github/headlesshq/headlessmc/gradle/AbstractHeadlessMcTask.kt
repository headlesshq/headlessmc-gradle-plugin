package io.github.headlesshq.headlessmc.gradle

import io.github.headlesshq.headlessmc.gradle.internal.Args
import io.github.headlesshq.headlessmc.gradle.internal.NativeDownloader
import io.github.headlesshq.headlessmc.gradle.internal.NativePlatform
import io.github.headlesshq.headlessmc.gradle.internal.ProjectScope
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.process.ExecOperations
import org.gradle.process.ExecResult
import org.gradle.work.DisableCachingByDefault
import java.io.File
import javax.inject.Inject

/**
 * Base class for tasks that run HeadlessMc.
 * HeadlessMc is started as a separate process, either on a Java toolchain or as native executable,
 * and is configured to only place files inside the project.
 * The properties are set from the [HeadlessMcExtension] by default.
 */
@DisableCachingByDefault(because = "Runs HeadlessMc, which has side effects outside of Gradle's knowledge")
abstract class AbstractHeadlessMcTask : DefaultTask() {
    @get:Input
    abstract val headlessMcVersion: Property<String>

    /**
     * The Maven group of HeadlessMc, only used for messages, the jar is [headlessMcJar].
     */
    @get:Internal
    abstract val headlessMcGroup: Property<String>

    @get:Input
    abstract val distribution: Property<HmcDistribution>

    /**
     * The Java HeadlessMc runs on with [HmcDistribution.JVM].
     */
    @get:Nested
    @get:Optional
    abstract val javaLauncher: Property<JavaLauncher>

    /**
     * The HeadlessMc runner jar, only resolved when HeadlessMc runs on the JVM.
     */
    @get:Internal
    abstract val headlessMcJar: ConfigurableFileCollection

    /**
     * JVM arguments for HeadlessMc with [HmcDistribution.JVM].
     */
    @get:Input
    abstract val jvmArgs: ListProperty<String>

    /**
     * The root directory of the build, all directories have to be inside it.
     */
    @get:Internal
    abstract val scopeDirectory: DirectoryProperty

    /**
     * The directory HeadlessMc runs in.
     */
    @get:Internal
    abstract val workingDirectory: DirectoryProperty

    /**
     * `hmc.files.location`, see [HeadlessMcExtension.launcherDirectory].
     */
    @get:Internal
    abstract val launcherDirectory: DirectoryProperty

    /**
     * `hmc.files.mc`, see [HeadlessMcExtension.mcDirectory].
     */
    @get:Internal
    abstract val mcDirectory: DirectoryProperty

    /**
     * `hmc.files.game`, the directory the client runs in.
     * If not set HeadlessMc uses a directory for each version in the [mcDirectory].
     */
    @get:Internal
    abstract val gameDirectory: DirectoryProperty

    @get:Internal
    abstract val nativeDirectory: DirectoryProperty

    /**
     * A native executable to use, see [HeadlessMcExtension.executable].
     */
    @get:Internal
    abstract val executable: RegularFileProperty

    @get:Input
    abstract val nativeDownloadUrl: Property<String>

    @get:Input
    @get:Optional
    abstract val nativeSha256: Property<String>

    /**
     * HeadlessMc configuration, passed as system properties. Extends the extension's launcherConfig.
     */
    @get:Input
    abstract val launcherConfig: MapProperty<String, String>

    @get:Input
    abstract val jline: Property<Boolean>

    /**
     * Java installations HeadlessMc may use for the game (`hmc.java.versions`).
     * HeadlessMc also finds installed Java versions itself and can download missing ones into the launcher directory.
     */
    @get:Nested
    abstract val gameJavaLaunchers: ListProperty<JavaLauncher>

    /**
     * HeadlessMc plugin jars, synchronized into the plugins directory if [managePlugins].
     */
    @get:Classpath
    abstract val plugins: ConfigurableFileCollection

    @get:Input
    abstract val managePlugins: Property<Boolean>

    @get:Input
    abstract val ignoreExitValue: Property<Boolean>

    /**
     * If Gradle runs with --offline.
     */
    @get:Internal
    abstract val gradleOffline: Property<Boolean>

    @get:Inject
    protected abstract val execOperations: ExecOperations

    @get:Inject
    protected abstract val fileSystemOperations: FileSystemOperations

    @Transient
    private var runtime: ResolvedRuntime? = null

    private data class ResolvedRuntime(val command: List<String>, val jvm: Boolean)

    /**
     * Checks that all directories are inside the project.
     *
     * @return the project scope.
     */
    protected fun checkScope(): ProjectScope {
        val scope = ProjectScope(scopeDirectory.get().asFile)
        scope.check("launcherDirectory", launcherDirectory.get().asFile)
        scope.check("mcDirectory", mcDirectory.get().asFile)
        if (gameDirectory.isPresent) {
            scope.check("gameDirectory", gameDirectory.get().asFile)
        }

        if (!executable.isPresent) {
            scope.check("nativeDirectory", nativeDirectory.get().asFile)
        }

        ProjectScope.checkConfig("launcherConfig of $path", launcherConfig.get())
        ProjectScope.checkJvmArgs("jvmArgs of $path", jvmArgs.get())
        return scope
    }

    /**
     * Synchronizes [plugins] into the HeadlessMc plugins directory, if [managePlugins].
     */
    protected fun syncPlugins() {
        if (managePlugins.get()) {
            val pluginsDir = launcherDirectory.dir("plugins").get().asFile
            fileSystemOperations.sync {
                it.from(plugins)
                it.into(pluginsDir)
            }
        }
    }

    /**
     * Checks the scope, synchronizes the plugins and runs HeadlessMc once.
     *
     * @param args the arguments for HeadlessMc.
     * @return the result of the process.
     */
    protected fun prepareAndRun(args: List<String>): ExecResult {
        checkScope()
        syncPlugins()
        return runHeadlessMc(args)
    }

    /**
     * Runs HeadlessMc with the given arguments.
     *
     * @param args the arguments for HeadlessMc, e.g. `[launch, fabric, 1.21.4]`.
     * @return the result of the process.
     */
    protected fun runHeadlessMc(args: List<String>): ExecResult {
        val runtime = resolvedRuntime()
        val commandLine = runtime.command.toMutableList()
        val systemProperties = systemProperties().map { (key, value) -> "-D$key=$value" }
        // with the JVM the system properties are JVM arguments,
        // the native executable parses them from the start of its arguments
        commandLine.addAll(if (runtime.jvm) commandLine.size - 2 else commandLine.size, systemProperties)
        commandLine.addAll(args)

        logger.lifecycle("[HeadlessMc] {}", args.joinToString(" "))
        logger.info("[HeadlessMc] Command line: {}", commandLine)
        val result = execOperations.exec {
            it.commandLine = if (isWindows()) {
                listOf(commandLine.first()) + commandLine.drop(1).map(Args::quoteForWindows)
            } else {
                commandLine
            }
            it.workingDir = workingDirectory.get().asFile
            it.standardInput = System.`in`
            it.isIgnoreExitValue = true
        }

        if (result.exitValue != 0 && !ignoreExitValue.get()) {
            throw GradleException(
                "HeadlessMc exited with exit code ${result.exitValue} for '${args.joinToString(" ")}'," +
                    " see ${launcherDirectory.dir("logs").get().asFile}",
            )
        }

        return result
    }

    /**
     * @return the system properties configuring HeadlessMc, the managed hmc.files.* keys come last.
     */
    protected open fun systemProperties(): MutableMap<String, String> {
        val properties = LinkedHashMap<String, String>()
        properties["hmc.jline.enabled"] = jline.get().toString()
        val javaHomes = gameJavaLaunchers.get()
            .map { it.metadata.installationPath.asFile.absolutePath }
            .distinct()
        if (javaHomes.isNotEmpty()) {
            properties["hmc.java.versions"] = javaHomes.joinToString(",")
        }

        properties.putAll(launcherConfig.get())
        properties["hmc.files.location"] = launcherDirectory.get().asFile.absolutePath
        properties["hmc.files.mc"] = mcDirectory.get().asFile.absolutePath
        if (gameDirectory.isPresent) {
            properties["hmc.files.game"] = gameDirectory.get().asFile.absolutePath
        }

        return properties
    }

    private fun isWindows(): Boolean = System.getProperty("os.name", "").startsWith("Windows", ignoreCase = true)

    private fun resolvedRuntime(): ResolvedRuntime = runtime ?: resolveRuntime().also { runtime = it }

    private fun resolveRuntime(): ResolvedRuntime {
        val distribution = distribution.get()
        if (executable.isPresent) {
            val executable = executable.get().asFile
            if (!executable.isFile) {
                throw GradleException("The HeadlessMc executable $executable does not exist")
            }

            if (distribution == HmcDistribution.JVM) {
                throw GradleException("headlessmc.executable is set, but distribution is JVM")
            }

            return ResolvedRuntime(listOf(executable.absolutePath), false)
        }

        var jvmProblem: String? = null
        if (distribution != HmcDistribution.NATIVE) {
            val launcher = javaLauncher.orNull
            var jar: File? = null
            if (launcher == null) {
                jvmProblem = "no Java toolchain for HeadlessMc was found, HeadlessMc requires Java 25." +
                    " Install Java 25, configure toolchain auto-provisioning" +
                    " (e.g. the org.gradle.toolchains.foojay-resolver-convention settings plugin)" +
                    " or set headlessmc.javaLauncher"
            } else if (launcher.metadata.languageVersion.asInt() < 25) {
                jvmProblem = "HeadlessMc requires Java 25, but the Java launcher is Java ${launcher.metadata.languageVersion}"
            } else {
                val files = headlessMcJar.files
                if (files.size != 1) {
                    val group = headlessMcGroup.getOrElse(HeadlessMcPlugin.DEFAULT_GROUP)
                    jvmProblem = "the HeadlessMc jar $group:${HeadlessMcPlugin.APPLICATION_MODULE}:${headlessMcVersion.get()}" +
                        ":runner could not be resolved (found $files)." +
                        if (group.startsWith("com.github.")) {
                            " Add maven { url = 'https://jitpack.io' } to the repositories"
                        } else {
                            " Add mavenCentral() to the repositories"
                        }
                } else {
                    jar = files.single()
                }
            }

            if (launcher != null && jar != null) {
                val command = buildList {
                    add(launcher.executablePath.asFile.absolutePath)
                    addAll(jvmArgs.get())
                    add("-jar")
                    add(jar.absolutePath)
                }
                return ResolvedRuntime(command, true)
            }

            if (distribution == HmcDistribution.JVM) {
                throw GradleException("Cannot run HeadlessMc on the JVM: $jvmProblem")
            }
        }

        if (headlessMcGroup.getOrElse(HeadlessMcPlugin.DEFAULT_GROUP) != HeadlessMcPlugin.DEFAULT_GROUP) {
            throw GradleException(
                "Cannot run HeadlessMc ${headlessMcGroup.get()}:${headlessMcVersion.get()} natively," +
                    " there are only native executables of releases" +
                    if (jvmProblem == null) ", use distribution JVM" else ", and cannot run it on the JVM: $jvmProblem",
            )
        }

        val platform = NativePlatform.current()
            ?: throw GradleException(
                "HeadlessMc has no native executable for ${System.getProperty("os.name")}/${System.getProperty("os.arch")}" +
                    if (jvmProblem == null) "" else " and $jvmProblem",
            )

        if (jvmProblem != null) {
            logger.info("[HeadlessMc] Using the native executable, since {}", jvmProblem)
        }

        val version = headlessMcVersion.get()
        val executable = nativeDirectory.get().asFile.toPath().resolve(version).resolve(platform.assetName)
        NativeDownloader(logger).ensure(
            executable, nativeDownloadUrl.get(), version, platform, nativeSha256.orNull, gradleOffline.get(),
        )
        return ResolvedRuntime(listOf(executable.toAbsolutePath().toString()), false)
    }
}
