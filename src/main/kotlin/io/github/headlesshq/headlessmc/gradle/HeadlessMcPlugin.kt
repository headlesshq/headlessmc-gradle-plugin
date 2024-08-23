package io.github.headlesshq.headlessmc.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * The HeadlessMc plugin entry point.
 *
 * @author 3arthqu4ke
 */
class HeadlessMcPlugin: Plugin<Project> {
    private val pluginVersion: String = "2.1.0"

    override fun apply(project: Project) {
        project.logger.lifecycle("[HeadlessMc] Plugin Version: $pluginVersion")
    }

}