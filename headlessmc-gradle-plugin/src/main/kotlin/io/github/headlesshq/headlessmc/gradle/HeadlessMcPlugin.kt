package io.github.headlesshq.headlessmc.gradle

import io.github.headlesshq.headlessmc.gradle.internal.NativeDownloader
import io.github.headlesshq.headlessmc.gradle.internal.PluginInfo
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JvmToolchainsPlugin
import org.gradle.api.provider.Provider
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService
import java.nio.file.Path

/**
 * The HeadlessMc Gradle plugin.
 *
 * * `headlessmc`: the [HeadlessMcExtension].
 * * `headlessmcRuntime`: the HeadlessMc runner jar, by default
 *   `io.github.headlesshq:headlessmc-application:<version>:runner`.
 * * `headlessmcPlugins`: HeadlessMc plugin jars, installed into the project's HeadlessMc.
 * * `headlessmc` task: a [HeadlessMcExec], `./gradlew headlessmc --args="..."`.
 *
 * Register [HeadlessMcRunTask]s to launch the game.
 */
open class HeadlessMcPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply(JvmToolchainsPlugin::class.java)
        val logger = project.logger
        val extension = project.extensions.create(EXTENSION, HeadlessMcExtension::class.java)
        extension.version.convention(PluginInfo.headlessMcVersion)
        extension.group.convention(DEFAULT_GROUP)
        extension.distribution.convention(HmcDistribution.AUTO)
        extension.javaVersion.convention(25)
        extension.directory.convention(project.layout.projectDirectory.dir("run").dir("headlessmc"))
        extension.launcherDirectory.convention(extension.directory.dir("launcher"))
        extension.mcDirectory.convention(extension.directory.dir("mc"))
        extension.nativeDirectory.convention(extension.directory.dir("native"))
        extension.nativeDownloadUrl.convention(NativeDownloader.GITHUB_RELEASES)
        extension.jline.convention(false)
        extension.managePlugins.convention(true)

        val toolchains = project.extensions.getByType(JavaToolchainService::class.java)
        // AUTO falls back to the native executable, so a missing toolchain must not fail the build
        val toolchain: Provider<JavaLauncher> = project.providers.provider {
            try {
                toolchains.launcherFor { it.languageVersion.set(extension.javaVersion.map(JavaLanguageVersion::of)) }.orNull
            } catch (e: RuntimeException) {
                logger.info("[HeadlessMc] No Java {} toolchain found: {}", extension.javaVersion.get(), e.toString())
                null
            }
        }
        extension.javaLauncher.convention(toolchain)

        val runtime = project.configurations.create(RUNTIME_CONFIGURATION) { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = true
            configuration.isTransitive = false
            configuration.description = "The HeadlessMc jar, run with Java 25."
            configuration.defaultDependencies { dependencies ->
                val includedBuild = extension.includedBuild.orNull
                dependencies.add(
                    if (includedBuild != null) {
                        project.dependencies.create(
                            project.files(includedApplication(project, includedBuild).resolve("build/quarkus-app/quarkus-run.jar")),
                        )
                    } else {
                        project.dependencies.create("${extension.group.get()}:$APPLICATION_MODULE:${extension.version.get()}:runner")
                    },
                )
            }
        }

        val plugins = project.configurations.create(PLUGINS_CONFIGURATION) { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = true
            // plugin jars nest their libraries
            configuration.isTransitive = false
            configuration.description = "HeadlessMc plugins, installed into the HeadlessMc of this project."
        }

        val rootDirectory = project.rootProject.layout.projectDirectory
        val offline = project.providers.provider { project.gradle.startParameter.isOffline }
        project.tasks.withType(AbstractHeadlessMcTask::class.java).configureEach { task ->
            task.group = GROUP
            task.headlessMcVersion.convention(extension.version)
            task.headlessMcGroup.convention(extension.group)
            task.dependsOn(
                extension.includedBuild
                    .map { listOf(project.gradle.includedBuild(it).task(":$APPLICATION_MODULE:quarkusBuild")) }
                    .orElse(emptyList()),
            )
            task.distribution.convention(extension.distribution)
            task.javaLauncher.convention(extension.javaLauncher)
            // lenient, so that a missing repository only fails if HeadlessMc actually runs on the JVM
            task.headlessMcJar.from(
                task.distribution.map<FileCollection> { distribution ->
                    if (distribution == HmcDistribution.NATIVE) {
                        project.files()
                    } else {
                        runtime.incoming.artifactView { it.isLenient = true }.files
                    }
                },
            )
            task.jvmArgs.convention(extension.jvmArgs)
            task.scopeDirectory.convention(rootDirectory)
            task.workingDirectory.convention(project.layout.projectDirectory)
            task.launcherDirectory.convention(extension.launcherDirectory)
            task.mcDirectory.convention(extension.mcDirectory)
            task.nativeDirectory.convention(extension.nativeDirectory)
            task.executable.convention(extension.executable)
            task.nativeDownloadUrl.convention(extension.nativeDownloadUrl)
            task.nativeSha256.convention(extension.nativeSha256)
            task.launcherConfig.putAll(extension.launcherConfig)
            task.jline.convention(extension.jline)
            task.plugins.from(plugins)
            task.managePlugins.convention(extension.managePlugins)
            task.ignoreExitValue.convention(false)
            task.gradleOffline.convention(offline)
        }

        project.tasks.withType(HeadlessMcRunTask::class.java).configureEach { task ->
            task.description = "Launches Minecraft with HeadlessMc."
            task.platform.convention(HmcPlatform.VANILLA)
            task.server.convention(false)
            task.headless.convention(false)
            task.offline.convention(false)
            task.dummyAssets.convention(false)
            task.acceptEula.convention(false)
            task.cleanDestination.convention(true)
            task.gameDirectory.convention(extension.directory.dir("game").map { it.dir(task.name) })
        }

        project.tasks.register(EXEC_TASK, HeadlessMcExec::class.java) {
            it.description = "Runs HeadlessMc, e.g. ./gradlew headlessmc --args=\"version list\"."
        }

        // so that build.gradle files can use the types without imports
        val extra = project.extensions.extraProperties
        listOf(HeadlessMcRunTask::class.java, HeadlessMcExec::class.java, HmcDistribution::class.java, HmcPlatform::class.java)
            .forEach { extra.set(it.simpleName, it) }
    }

    companion object {
        const val EXTENSION: String = "headlessmc"
        const val RUNTIME_CONFIGURATION: String = "headlessmcRuntime"
        const val PLUGINS_CONFIGURATION: String = "headlessmcPlugins"
        const val EXEC_TASK: String = "headlessmc"
        const val GROUP: String = "headlessmc"
        const val DEFAULT_GROUP: String = "io.github.headlesshq"
        const val JITPACK_GROUP: String = "com.github.headlesshq.headlessmc"
        const val APPLICATION_MODULE: String = "headlessmc-application"

        /**
         * @param project the project.
         * @param includedBuild the name of an included build of HeadlessMc.
         * @return the directory of the headlessmc-application project of the included build.
         */
        @JvmStatic
        fun includedApplication(project: Project, includedBuild: String): Path =
            project.gradle.includedBuild(includedBuild).projectDir.toPath().resolve(APPLICATION_MODULE)
    }
}
