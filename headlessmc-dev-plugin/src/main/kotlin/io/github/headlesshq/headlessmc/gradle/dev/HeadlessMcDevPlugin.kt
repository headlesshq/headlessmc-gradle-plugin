package io.github.headlesshq.headlessmc.gradle.dev

import io.github.headlesshq.headlessmc.gradle.AbstractHeadlessMcTask
import io.github.headlesshq.headlessmc.gradle.HeadlessMcExtension
import io.github.headlesshq.headlessmc.gradle.HeadlessMcPlugin
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.logging.Logging
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion

/**
 * Builds HeadlessMc plugins.
 *
 * * `headlessmcPlugin`: the [HeadlessMcPluginExtension], describes the plugin.
 * * `headlessmc`: the HeadlessMc runtime, the plugin is compiled and tested against it. Never packaged.
 * * `include`: libraries nested into the plugin jar (Jar-in-Jar) under META-INF/jars, with
 *   their transitive dependencies, except for the ones HeadlessMc already contains.
 * * `generatePluginManifest`: writes META-INF/headlessmc-plugin.json.
 * * `generatePluginIndexEntry`: writes build/headlessmc-plugin/index-entry.json, the entry of the plugin jar
 *   for the index.json of headlessmc-plugins.
 * * `installPlugin`: installs the plugin jar and the `headlessmcPlugins` into the HeadlessMc of
 *   the project, which lives in `run/headlessmc`.
 * * Applies the [HeadlessMcPlugin], every HeadlessMc task, e.g.
 *   `./gradlew headlessmc --args="plugin ls"`, runs the HeadlessMc of the project with this plugin installed.
 */
open class HeadlessMcDevPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply(JavaLibraryPlugin::class.java)
        project.pluginManager.apply(HeadlessMcPlugin::class.java)
        val headlessMcExtension = project.extensions.getByType(HeadlessMcExtension::class.java)
        val extension = project.extensions.create(EXTENSION, HeadlessMcPluginExtension::class.java)
        // plugins need a newer HeadlessMc than the default of the HeadlessMc plugin
        headlessMcExtension.group.convention(Defaults.GROUP)
        headlessMcExtension.version.convention(Defaults.VERSION)
        extension.headlessmcVersion.convention(headlessMcExtension.version)
        // the default HeadlessMc may be a snapshot of the next release
        extension.headlessmc.convention(extension.headlessmcVersion.map { if (it == Defaults.VERSION) Defaults.COMPATIBLE else it })
        extension.name.convention(extension.id)
        extension.java.convention(GeneratePluginManifest.MIN_JAVA)
        extension.failOnError.convention(true)
        extension.mutableJar.convention(
            project.layout.file(
                headlessMcExtension.includedBuild.map {
                    HeadlessMcPlugin.includedApplication(project, it).resolve("build/headlessmc-mutable.zip").toFile()
                },
            ),
        )
        // compile with the Java version the plugin needs, Gradle itself may run on an older Java
        project.extensions.getByType(JavaPluginExtension::class.java).toolchain.languageVersion
            .convention(extension.java.map(JavaLanguageVersion::of))

        val objects = project.objects
        val headlessmc = project.configurations.create(HEADLESSMC_CONFIGURATION) { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = true
            configuration.description = "The HeadlessMc runtime the plugin is compiled against, never packaged."
            configuration.attributes { runtimeAttributes(objects, it) }
        }
        project.dependencies.addProvider(
            HEADLESSMC_CONFIGURATION,
            extension.headlessmcVersion.map { "${headlessMcExtension.group.get()}:${HeadlessMcPlugin.APPLICATION_MODULE}:$it" },
        )
        // compile against the complete runtime classpath of HeadlessMc, it is exactly what the plugin runs with,
        // the api scope of the HeadlessMc modules misses e.g. the CDI annotations
        project.dependencies.add("compileOnly", project.files(headlessmc))
        project.dependencies.add("testImplementation", project.files(headlessmc))

        val include = project.configurations.create(INCLUDE_CONFIGURATION) { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = false
            configuration.description = "Libraries to nest into the plugin jar (Jar-in-Jar)."
        }
        project.configurations.named("implementation") { it.extendsFrom(include) }
        val includeClasspath = project.configurations.create("includeClasspath") { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = true
            configuration.extendsFrom(include)
            configuration.attributes { runtimeAttributes(objects, it) }
        }

        // group:artifact -> version of the libraries HeadlessMc contains
        val coreModules = headlessmc.incoming.artifacts.resolvedArtifacts.map(::modules)
        val nested = includeClasspath.incoming.artifacts.resolvedArtifacts.zip(coreModules, ::withoutCoreModules)
        val nestedFiles = nested.map { artifacts -> artifacts.map { it.file } }

        val pluginVersion = project.provider { project.version.toString() }
        val manifest = project.tasks.register("generatePluginManifest", GeneratePluginManifest::class.java) { task ->
            task.group = "build"
            task.description = "Generates META-INF/${GeneratePluginManifest.FILE_NAME}."
            task.pluginId.set(extension.id)
            task.pluginVersion.set(pluginVersion)
            task.pluginName.set(extension.name)
            task.pluginDescription.set(extension.description)
            task.authors.set(extension.authors)
            task.license.set(extension.license)
            task.homepage.set(extension.homepage)
            task.headlessmc.set(extension.headlessmc)
            task.java.set(extension.java)
            task.dependencies.set(extension.dependencies)
            task.optionalDependencies.set(extension.optionalDependencies)
            task.libraryVersions.set(extension.libraryVersions)
            task.libraries.set(
                nested.map { artifacts ->
                    artifacts.map { artifact ->
                        val id = artifact.id.componentIdentifier as ModuleComponentIdentifier
                        "${id.group}:${id.module}:${id.version}:${artifact.file.name}"
                    }
                },
            )
            task.libraryFiles.from(nestedFiles)
            task.output.set(project.layout.buildDirectory.file("generated/headlessmc/${GeneratePluginManifest.FILE_NAME}"))
        }

        val jar = project.tasks.named("jar", Jar::class.java)
        jar.configure { task ->
            task.from(manifest) { it.into("META-INF") }
            task.from(nestedFiles) { it.into(GeneratePluginManifest.JARS_DIR) }
        }

        val jarFile = jar.flatMap { it.archiveFile }
        extension.downloadUrl.convention(
            extension.homepage.zip(pluginVersion) { homepage, version -> homepage to version }
                .zip(jarFile) { (homepage, version), file ->
                    GeneratePluginIndexEntry.defaultDownloadUrl(homepage, version, file.asFile.name).orEmpty()
                }
                .filter { it.isNotEmpty() },
        )
        project.tasks.register("generatePluginIndexEntry", GeneratePluginIndexEntry::class.java) { task ->
            task.group = "build"
            task.description = "Generates the entry of the plugin jar for the index.json of headlessmc-plugins."
            task.pluginId.set(extension.id)
            task.pluginVersion.set(pluginVersion)
            task.pluginName.set(extension.name)
            task.pluginDescription.set(extension.description)
            task.authors.set(extension.authors)
            task.license.set(extension.license)
            task.homepage.set(extension.homepage)
            task.icon.set(extension.icon)
            task.headlessmc.set(extension.headlessmc)
            task.java.set(extension.java)
            task.downloadUrl.set(extension.downloadUrl)
            task.jar.set(jarFile)
            task.output.set(project.layout.buildDirectory.file("headlessmc-plugin/${GeneratePluginIndexEntry.FILE_NAME}"))
        }

        project.tasks.withType(JavaCompile::class.java).configureEach { task ->
            task.options.encoding = "UTF-8"
            task.options.release.convention(extension.java)
        }

        // every HeadlessMc task runs the HeadlessMc of the project with this plugin installed
        project.tasks.withType(AbstractHeadlessMcTask::class.java).configureEach { task ->
            task.plugins.from(jar)
            task.launcherConfig.put("hmc.plugins.fail-on-error", extension.failOnError.map { it.toString() })
            // an included build of HeadlessMc is re-built with the plugins from its own mutable jar
            task.dependsOn(
                headlessMcExtension.includedBuild
                    .map { listOf(project.gradle.includedBuild(it).task(":${HeadlessMcPlugin.APPLICATION_MODULE}:mutableJarZip")) }
                    .orElse(emptyList()),
            )
            task.launcherConfig.putAll(
                extension.mutableJar.map { mapOf("hmc.plugins.mutable-jar" to it.asFile.absolutePath) }.orElse(emptyMap()),
            )
        }

        project.tasks.register("installPlugin", Sync::class.java) { task ->
            task.group = HeadlessMcPlugin.GROUP
            task.description = "Installs the plugin into the HeadlessMc of the project, run it with ./gradlew headlessmc."
            task.from(jar)
            task.from(project.configurations.named(HeadlessMcPlugin.PLUGINS_CONFIGURATION))
            task.into(headlessMcExtension.launcherDirectory.dir("plugins"))
        }
    }

    companion object {
        const val EXTENSION: String = "headlessmcPlugin"
        const val HEADLESSMC_CONFIGURATION: String = "headlessmc"
        const val INCLUDE_CONFIGURATION: String = "include"
        private val LOGGER = Logging.getLogger(HeadlessMcDevPlugin::class.java)

        private fun modules(artifacts: Collection<ResolvedArtifactResult>): Map<String, String> =
            artifacts.mapNotNull { it.id.componentIdentifier as? ModuleComponentIdentifier }
                .associate { "${it.group}:${it.module}" to it.version }

        private fun withoutCoreModules(
            artifacts: Collection<ResolvedArtifactResult>,
            coreModules: Map<String, String>,
        ): List<ResolvedArtifactResult> = artifacts.filter { artifact ->
            val componentId = artifact.id.componentIdentifier
            val id = componentId as? ModuleComponentIdentifier
                ?: throw GradleException("Only external modules can be included, not ${componentId.displayName}")
            val module = "${id.group}:${id.module}"
            val coreVersion = coreModules[module]
            if (coreVersion != null) {
                checkCoreVersion(module, id.version, coreVersion)
            }

            coreVersion == null
        }

        /**
         * HeadlessMc always uses its own version of the libraries it contains.
         * Fails if the plugin needs a different major version, warns if it needs a newer one.
         */
        @JvmStatic
        internal fun checkCoreVersion(module: String, version: String, coreVersion: String) {
            if (version == coreVersion) {
                return
            }

            if (Versions.major(version) != Versions.major(coreVersion)) {
                throw GradleException(
                    "The plugin needs $module:$version, but HeadlessMc ships $coreVersion" +
                        " and plugins cannot replace libraries of HeadlessMc.",
                )
            }

            if (Versions.compare(version, coreVersion) > 0) {
                LOGGER.warn(
                    "The plugin needs {}:{}, but HeadlessMc ships the older {}, which will be used at runtime.",
                    module, version, coreVersion,
                )
            }
        }

        private fun runtimeAttributes(objects: ObjectFactory, attributes: AttributeContainer) {
            attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
            attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, Category.LIBRARY))
            attributes.attribute(
                LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                objects.named(LibraryElements::class.java, LibraryElements.JAR),
            )
            attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling::class.java, Bundling.EXTERNAL))
        }
    }
}
