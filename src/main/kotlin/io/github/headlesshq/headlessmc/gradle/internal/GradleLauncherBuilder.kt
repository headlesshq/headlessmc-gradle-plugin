package io.github.headlesshq.headlessmc.gradle.internal

import io.github.headlesshq.headlessmc.gradle.HeadlessMcRunTask
import me.earth.headlessmc.launcher.LauncherBuilder
import me.earth.headlessmc.launcher.auth.AccountStore
import me.earth.headlessmc.launcher.auth.AccountValidator
import me.earth.headlessmc.launcher.auth.OfflineChecker
import me.earth.headlessmc.launcher.files.FileManager
import me.earth.headlessmc.launcher.files.LauncherConfig
import me.earth.headlessmc.launcher.java.JavaService
import me.earth.headlessmc.launcher.os.OSFactory
import me.earth.headlessmc.launcher.version.VersionService
import java.util.*
import java.util.logging.Level

internal class GradleLauncherBuilder(private val task: HeadlessMcRunTask) : LauncherBuilder() {
    init {
        fileManager(FileManager.mkdir(task.launcherDirectory.asFile.get().absolutePath))
        loggingService().setPathFactory { task.launcherDirectory.asFile.get().toPath().resolve("headlessmc.log") }
        commandLine().inAndOutProvider.setOut { System.out }
        commandLine().inAndOutProvider.setErr { System.err }
        exitManager().setExitManager { exitCode ->
            if (exitCode != 0) {
                throw IllegalStateException("Launcher exited with exit code $exitCode")
            }
        }
    }

    override fun initDefaultServices(): LauncherBuilder {
        if (os() == null) {
            os(OSFactory.detect(Objects.requireNonNull(configService(), "ConfigHolder was null!").config))
        }

        if (launcherConfig() == null) {
            val mcFiles =FileManager.mkdir(task.mcDirectory.asFile.get().path)
            val gameDir = FileManager.mkdir(task.gameDirectory.asFile.get().path)
            launcherConfig(LauncherConfig(
                Objects.requireNonNull(configService(), "ConfigHolder was null!"),
                mcFiles,
                gameDir
            ))
        }

        if (versionService() == null) {
            versionService(VersionService(Objects.requireNonNull(this.launcherConfig(), "LauncherConfig!")))
        }

        if (javaService() == null) {
            javaService(JavaService(Objects.requireNonNull(configService(), "ConfigHolder was null!")))
        }

        return this
    }

    override fun initLogging(): LauncherBuilder {
        loggingService().setStreamFactory { System.out }
        super.initLogging()
        loggingService().setLevel(Level.INFO)
        return this
    }

    override fun initAccountManager(): LauncherBuilder {
        if (accountManager() == null) {
            val launcherConfig = Objects.requireNonNull(launcherConfig(), "LauncherConfig was null!")
            val configService = Objects.requireNonNull(configService(), "ConfigHolder was null!")
            val accountStore = AccountStore(launcherConfig)
            accountManager(GradleAccountManager(AccountValidator(), OfflineChecker(configService), accountStore))
            accountManager().load(configService.config)
        }

        return this
    }

    override fun configureProcessFactory(): LauncherBuilder {
        if (processFactory() == null) {
            processFactory(
                GradleProcessFactory(
                    task,
                    Objects.requireNonNull(downloadService(), "Download Service was null!"),
                    Objects.requireNonNull(launcherConfig(), "McFiles were null!"),
                    Objects.requireNonNull(os(), "OS was null!")
                )
            )
        }

        return this
    }

    override fun build(): GradleLauncher {
        return GradleLauncher(
            Objects.requireNonNull(headlessMc(), "HeadlessMc was null!"),
            Objects.requireNonNull(versionService(), "VersionService was null!"),
            Objects.requireNonNull(launcherConfig(), "LauncherConfig was null!"),
            Objects.requireNonNull(sha1Service(), "Sha1Service was null!"),
            Objects.requireNonNull(downloadService(), "Download Service was null"),
            Objects.requireNonNull(processFactory(), "ProcessFactory was null!"),
            Objects.requireNonNull(configService(), "ConfigService was null!"),
            Objects.requireNonNull(javaService(), "JavaService was null!"),
            Objects.requireNonNull(accountManager(), "AccountManager was null!"),
            Objects.requireNonNull(versionSpecificModManager(), "VersionSpecificModManager was null!"),
            Objects.requireNonNull(pluginManager(), "PluginManager was null!")
        )
    }

}