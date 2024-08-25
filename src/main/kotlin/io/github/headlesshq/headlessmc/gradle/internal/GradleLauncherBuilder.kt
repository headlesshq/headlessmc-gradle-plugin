package io.github.headlesshq.headlessmc.gradle.internal

import io.github.headlesshq.headlessmc.gradle.HeadlessMcRunTask
import me.earth.headlessmc.launcher.LauncherBuilder
import me.earth.headlessmc.launcher.Service
import me.earth.headlessmc.launcher.auth.AccountStore
import me.earth.headlessmc.launcher.auth.AccountValidator
import me.earth.headlessmc.launcher.auth.OfflineChecker
import me.earth.headlessmc.launcher.files.ConfigService
import me.earth.headlessmc.launcher.files.FileManager
import me.earth.headlessmc.launcher.files.LauncherConfig
import me.earth.headlessmc.launcher.java.JavaService
import me.earth.headlessmc.launcher.os.OSFactory
import me.earth.headlessmc.launcher.version.VersionService
import java.util.Objects.requireNonNull
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

    override fun initConfigService(): LauncherBuilder {
        return ifNull(
            { obj: LauncherBuilder -> obj.configService() },
            { obj: LauncherBuilder, configService: ConfigService? -> obj.configService(configService) },
            { Service.refresh(GradleConfigService(task, requireNonNull(fileManager(), "FileManager not initialized"))) }
        )
    }

    override fun initDefaultServices(): LauncherBuilder {
        if (os() == null) {
            os(OSFactory.detect(requireNonNull(configService(), "ConfigHolder was null!").config))
        }

        if (launcherConfig() == null) {
            val mcFiles =FileManager.mkdir(task.mcDirectory.asFile.get().path)
            val gameDir = FileManager.mkdir(task.gameDirectory.asFile.get().path)
            launcherConfig(LauncherConfig(
                requireNonNull(configService(), "ConfigHolder was null!"),
                mcFiles,
                gameDir
            ))
        }

        if (versionService() == null) {
            versionService(VersionService(requireNonNull(this.launcherConfig(), "LauncherConfig!")))
        }

        if (javaService() == null) {
            javaService(JavaService(requireNonNull(configService(), "ConfigHolder was null!")))
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
            val launcherConfig = requireNonNull(launcherConfig(), "LauncherConfig was null!")
            val configService = requireNonNull(configService(), "ConfigHolder was null!")
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
                    requireNonNull(downloadService(), "Download Service was null!"),
                    requireNonNull(launcherConfig(), "McFiles were null!"),
                    requireNonNull(os(), "OS was null!")
                )
            )
        }

        return this
    }

    override fun build(): GradleLauncher {
        return GradleLauncher(
            requireNonNull(headlessMc(), "HeadlessMc was null!"),
            requireNonNull(versionService(), "VersionService was null!"),
            requireNonNull(launcherConfig(), "LauncherConfig was null!"),
            requireNonNull(sha1Service(), "Sha1Service was null!"),
            requireNonNull(downloadService(), "Download Service was null"),
            requireNonNull(processFactory(), "ProcessFactory was null!"),
            requireNonNull(configService(), "ConfigService was null!"),
            requireNonNull(javaService(), "JavaService was null!"),
            requireNonNull(accountManager(), "AccountManager was null!"),
            requireNonNull(versionSpecificModManager(), "VersionSpecificModManager was null!"),
            requireNonNull(pluginManager(), "PluginManager was null!")
        )
    }

}