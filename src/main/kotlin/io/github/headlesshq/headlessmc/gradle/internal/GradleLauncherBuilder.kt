package io.github.headlesshq.headlessmc.gradle.internal

import io.github.headlesshq.headlessmc.gradle.HeadlessMcRunTask
import me.earth.headlessmc.launcher.LauncherBuilder
import me.earth.headlessmc.launcher.auth.AccountStore
import me.earth.headlessmc.launcher.auth.AccountValidator
import me.earth.headlessmc.launcher.auth.OfflineChecker
import me.earth.headlessmc.launcher.files.FileManager
import java.util.*
import java.util.logging.Level

internal class GradleLauncherBuilder(private val task: HeadlessMcRunTask) : LauncherBuilder() {
    init {
        fileManager(FileManager.mkdir(task.launcherDirectory.asFile.get().absolutePath))
        mcFiles(FileManager.mkdir(task.mcDirectory.asFile.get().path))
        gameDir(FileManager.mkdir(task.gameDirectory.asFile.get().path))
        loggingService().setPathFactory { task.launcherDirectory.asFile.get().toPath().resolve("headlessmc.log") }
        commandLine().inAndOutProvider.setOut { System.out }
        commandLine().inAndOutProvider.setErr { System.err }
        exitManager().setExitManager { exitCode ->
            if (exitCode != 0) {
                throw IllegalStateException("Launcher exited with exit code $exitCode")
            }
        }
    }

    override fun initLogging(): LauncherBuilder {
        loggingService().setStreamFactory { System.out }
        super.initLogging()
        loggingService().setLevel(Level.INFO)
        return this
    }

    override fun initAccountManager(): LauncherBuilder {
        if (accountManager() == null) {
            val fileManager = Objects.requireNonNull(fileManager(), "FileManager was null!")
            val configService = Objects.requireNonNull(configService(), "ConfigHolder was null!")
            val accountStore = AccountStore(fileManager, configService)
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
                    Objects.requireNonNull(mcFiles(), "McFiles were null!"),
                    Objects.requireNonNull(configService(), "ConfigHolder was null!"),
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
            Objects.requireNonNull(mcFiles(), "McFiles were null!"),
            Objects.requireNonNull(gameDir(), "GameDir was null!"),
            Objects.requireNonNull(sha1Service(), "Sha1Service was null!"),
            Objects.requireNonNull(downloadService(), "Download Service was null"),
            Objects.requireNonNull(fileManager(), "FileManager was null!"),
            Objects.requireNonNull(processFactory(), "ProcessFactory was null!"),
            Objects.requireNonNull(configService(), "ConfigService was null!"),
            Objects.requireNonNull(javaService(), "JavaService was null!"),
            Objects.requireNonNull(accountManager(), "AccountManager was null!"),
            Objects.requireNonNull(versionSpecificModManager(), "VersionSpecificModManager was null!"),
            Objects.requireNonNull(pluginManager(), "PluginManager was null!")
        )
    }

}