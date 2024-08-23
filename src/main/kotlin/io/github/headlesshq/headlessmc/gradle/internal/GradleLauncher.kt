package io.github.headlesshq.headlessmc.gradle.internal

import me.earth.headlessmc.api.HeadlessMc
import me.earth.headlessmc.launcher.Launcher
import me.earth.headlessmc.launcher.auth.AccountManager
import me.earth.headlessmc.launcher.download.ChecksumService
import me.earth.headlessmc.launcher.download.DownloadService
import me.earth.headlessmc.launcher.files.ConfigService
import me.earth.headlessmc.launcher.files.FileManager
import me.earth.headlessmc.launcher.java.JavaService
import me.earth.headlessmc.launcher.launch.ProcessFactory
import me.earth.headlessmc.launcher.plugin.PluginManager
import me.earth.headlessmc.launcher.specifics.VersionSpecificModManager
import me.earth.headlessmc.launcher.version.VersionService

/**
 * The [HeadlessMc] Launcher for Gradle.
 *
 * @author 3arthqu4ke
 */
internal class GradleLauncher(
    headlessMc: HeadlessMc, versionService: VersionService, mcFiles: FileManager,
    gameDir: FileManager, sha1Service: ChecksumService, downloadService: DownloadService,
    fileManager: FileManager, processFactory: ProcessFactory, configService: ConfigService,
    javaService: JavaService, accountManager: AccountManager,
    versionSpecificModManager: VersionSpecificModManager, pluginManager: PluginManager
) : Launcher(
    headlessMc, versionService,
    mcFiles, gameDir, sha1Service, downloadService, fileManager, processFactory, configService, javaService,
    accountManager, versionSpecificModManager, pluginManager
)
