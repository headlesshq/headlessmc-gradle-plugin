package io.github.headlesshq.headlessmc.gradle.internal

import io.github.headlesshq.headlessmc.gradle.HeadlessMcRunTask
import me.earth.headlessmc.api.config.HasConfig
import me.earth.headlessmc.jline.JLineProperties
import me.earth.headlessmc.launcher.download.DownloadService
import me.earth.headlessmc.launcher.files.FileManager
import me.earth.headlessmc.launcher.launch.JavaLaunchCommandBuilder
import me.earth.headlessmc.launcher.launch.LaunchOptions
import me.earth.headlessmc.launcher.launch.ProcessFactory
import me.earth.headlessmc.launcher.launch.SystemPropertyHelper
import me.earth.headlessmc.launcher.os.OS
import me.earth.headlessmc.launcher.version.Version
import org.gradle.process.internal.streams.ForwardStdinStreamsHandler
import org.gradle.process.internal.streams.OutputStreamsForwarder
import java.util.concurrent.Executors

class GradleProcessFactory(
    private val runTask: HeadlessMcRunTask,
    downloadService: DownloadService,
    files: FileManager,
    config: HasConfig,
    os: OS
): ProcessFactory(downloadService, files, config, os) {
    override fun configureCommandBuilder(
        options: LaunchOptions,
        version: Version,
        classpath: MutableList<String>,
        natives: FileManager
    ): JavaLaunchCommandBuilder.JavaLaunchCommandBuilderBuilder {
        val vmArgs = ArrayList<String>(options.additionalJvmArgs)
        if (!runTask.jline) {
            vmArgs.add(SystemPropertyHelper.toSystemProperty(JLineProperties.ENABLED.name, "false"))
        }

        return super.configureCommandBuilder(options, version, classpath, natives)
            .jvmArgs(vmArgs)
    }

    override fun run(builder: ProcessBuilder): Process {
        // gradle seems to not like inheritIO
        builder
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)

        val process = super.run(builder)
        val executor = Executors.newFixedThreadPool(3)
        val inHandler = ForwardStdinStreamsHandler(System.`in`)
        val outHandler = OutputStreamsForwarder(System.out, System.err, true)

        inHandler.connectStreams(process, "Minecraft", executor)
        outHandler.connectStreams(process, "Minecraft", executor)

        try {
            inHandler.start()
            outHandler.start()
            process.waitFor()
        } finally {
            inHandler.stop()
            outHandler.stop()
        }

        return process
    }

}
