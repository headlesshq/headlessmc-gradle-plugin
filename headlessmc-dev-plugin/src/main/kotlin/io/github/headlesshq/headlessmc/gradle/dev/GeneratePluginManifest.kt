package io.github.headlesshq.headlessmc.gradle.dev

import io.github.headlesshq.headlessmc.gradle.internal.NativeDownloader
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files

/**
 * Generates `META-INF/headlessmc-plugin.json`, the manifest HeadlessMc reads from every plugin jar.
 */
@CacheableTask
abstract class GeneratePluginManifest : DefaultTask() {
    @get:Input
    abstract val pluginId: Property<String>

    @get:Input
    abstract val pluginVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val pluginName: Property<String>

    @get:Input
    @get:Optional
    abstract val pluginDescription: Property<String>

    @get:Input
    abstract val authors: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val license: Property<String>

    @get:Input
    @get:Optional
    abstract val homepage: Property<String>

    @get:Input
    abstract val headlessmc: Property<String>

    @get:Input
    abstract val java: Property<Int>

    @get:Input
    abstract val dependencies: MapProperty<String, String>

    @get:Input
    abstract val optionalDependencies: SetProperty<String>

    @get:Input
    abstract val libraryVersions: MapProperty<String, String>

    /**
     * group:artifact:version:fileName of every nested library.
     */
    @get:Input
    abstract val libraries: ListProperty<String>

    /**
     * The nested library jars, to compute their hashes.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val libraryFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun generate() {
        val id = pluginId.get()
        if (!ID.matches(id)) {
            throw GradleException("Invalid plugin id '$id', use lowercase letters, digits, - and _")
        }

        val java = java.get()
        if (java < MIN_JAVA) {
            throw GradleException("HeadlessMc requires at least Java $MIN_JAVA, but java is set to $java")
        }

        val optional = optionalDependencies.get()
        val dependencyVersions = dependencies.get().toSortedMap()
        optional.firstOrNull { it !in dependencyVersions }?.let {
            throw GradleException(
                "The optional dependency $it has no versions, declare it with optionalDependency '$it', '<versions>'",
            )
        }

        val manifest = linkedMapOf<String, Any>(
            "schemaVersion" to SCHEMA_VERSION,
            "id" to id,
            "version" to pluginVersion.get(),
        )
        pluginName.orNull?.let { manifest["name"] = it }
        pluginDescription.orNull?.let { manifest["description"] = it }
        manifest["authors"] = authors.get()
        license.orNull?.let { manifest["license"] = it }
        homepage.orNull?.let { manifest["homepage"] = it }
        manifest["headlessmc"] = headlessmc.get()
        manifest["java"] = java
        manifest["dependencies"] = dependencyVersions.map { (dependencyId, versions) ->
            linkedMapOf("id" to dependencyId, "versions" to versions, "optional" to (dependencyId in optional))
        }
        manifest["libraries"] = libraryManifests()
        Files.writeString(output.get().asFile.toPath(), Json.write(manifest))
    }

    private fun libraryManifests(): List<Map<String, Any>> {
        val filesByName = libraryFiles.files.associateBy { it.name }
        val libraryVersions = libraryVersions.get()
        val paths = HashSet<String>()
        return libraries.get().map { coordinates ->
            val (group, artifact, version, fileName) = coordinates.split(":", limit = 4)
            val path = "$JARS_DIR/$fileName"
            if (!paths.add(path)) {
                throw GradleException("Two included libraries are named $fileName")
            }

            val file = filesByName[fileName] ?: throw GradleException("Could not find the jar of included library $coordinates")
            val library = linkedMapOf<String, Any>("group" to group, "artifact" to artifact, "version" to version)
            libraryVersions["$group:$artifact"]?.let { library["versions"] = it }
            library["path"] = path
            library["sha256"] = NativeDownloader.sha256(file.toPath())
            library
        }
    }

    companion object {
        const val FILE_NAME: String = "headlessmc-plugin.json"
        const val JARS_DIR: String = "META-INF/jars"
        internal const val SCHEMA_VERSION: Int = 1
        internal const val MIN_JAVA: Int = 25
        private val ID = Regex("[a-z0-9][a-z0-9_-]*")
    }
}
