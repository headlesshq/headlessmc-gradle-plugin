package io.github.headlesshq.headlessmc.gradle.internal

import io.github.headlesshq.headlessmc.gradle.HeadlessMcRunTask
import me.earth.headlessmc.api.config.Config
import me.earth.headlessmc.api.config.Property
import me.earth.headlessmc.launcher.LauncherProperties
import me.earth.headlessmc.launcher.files.ConfigService
import me.earth.headlessmc.launcher.files.FileManager
import java.util.function.Supplier

class GradleConfigService(private val runTask: HeadlessMcRunTask, fileManager: FileManager) : ConfigService(fileManager) {
    override fun getConfig(): Config {
        return GradleConfig(runTask, super.getConfig())
    }

    class GradleConfig(private val runTask: HeadlessMcRunTask, private val config: Config): Config {
        override fun getName(): String {
            return config.name
        }

        override fun getId(): Int {
            return config.id
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any?> getValue(property: Property<T>, def: Supplier<T>): T {
            if (runTask.dummyAssets && property == LauncherProperties.DUMMY_ASSETS
                || runTask.rethrowLaunchExceptions && property == LauncherProperties.RE_THROW_LAUNCH_EXCEPTIONS) {
                runTask.logger.info("Override ${property.name} with true")
                return true as T
            }

            val runTaskOverride = runTask.launcherConfig[property.name]
            if (runTaskOverride != null) {
                runTask.logger.lifecycle("Found property override ${property.name}:${runTaskOverride}")
                return property.parse(runTaskOverride)
            }

            return config.getValue(property, def)
        }
    }

}