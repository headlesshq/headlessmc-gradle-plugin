package io.github.headlesshq.headlessmc.gradle.dev

import io.github.headlesshq.headlessmc.gradle.internal.NativeDownloader
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files
import java.nio.file.Path

/**
 * Generates the entry of the plugin for the `index.json` of
 * [headlessmc-plugins](https://github.com/headlesshq/headlessmc-plugins), which `plugin search` and
 * `plugin download` read. The entry has a single release, the [jar] built by this project.
 */
@CacheableTask
abstract class GeneratePluginIndexEntry : DefaultTask() {
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
    @get:Optional
    abstract val icon: Property<String>

    @get:Input
    abstract val headlessmc: Property<String>

    @get:Input
    abstract val java: Property<Int>

    /**
     * Where the [jar] can be downloaded from.
     */
    @get:Input
    @get:Optional
    abstract val downloadUrl: Property<String>

    /**
     * The final plugin jar.
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val jar: RegularFileProperty

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun generate() {
        val url = downloadUrl.orNull ?: throw GradleException(
            "Cannot derive the download url of the plugin from the homepage '${homepage.orNull ?: ""}', " +
                "only github.com homepages are supported. Set headlessmcPlugin.downloadUrl to the url of " +
                "${jar.get().asFile.name}.",
        )
        val entry = entry(
            pluginId.get(),
            pluginName.orNull,
            pluginDescription.orNull,
            authors.get(),
            license.orNull,
            homepage.orNull,
            icon.orNull,
            release(pluginVersion.get(), headlessmc.get(), java.get(), url, jar.get().asFile.toPath()),
        )
        Files.writeString(output.get().asFile.toPath(), Json.write(entry))
    }

    companion object {
        const val FILE_NAME: String = "index-entry.json"
        private val GITHUB = Regex("https?://(?:www\\.)?github\\.com/([^/?#]+)/([^/?#]+?)(?:\\.git)?(?:[/?#].*)?")

        /**
         * The url of a jar attached to the GitHub release of the given version,
         * `https://github.com/<owner>/<repo>/releases/download/<version>/<fileName>`.
         *
         * @param homepage the homepage of the plugin, any url of its GitHub repository.
         * @return the url, or `null` if the homepage is not on github.com.
         */
        @JvmStatic
        fun defaultDownloadUrl(homepage: String?, version: String, fileName: String): String? {
            val match = homepage?.trim()?.let(GITHUB::matchEntire) ?: return null
            val (owner, repository) = match.destructured
            return "https://github.com/$owner/$repository/releases/download/$version/$fileName"
        }

        /**
         * A `PluginIndex.Entry` of headlessmc-plugins, absent values are left out.
         */
        @JvmStatic
        internal fun entry(
            id: String,
            name: String?,
            description: String?,
            authors: List<String>,
            license: String?,
            homepage: String?,
            icon: String?,
            release: Map<String, Any>,
        ): Map<String, Any> {
            val entry = linkedMapOf<String, Any>("id" to id)
            name?.let { entry["name"] = it }
            description?.let { entry["description"] = it }
            entry["authors"] = authors
            license?.let { entry["license"] = it }
            homepage?.let { entry["homepage"] = it }
            icon?.let { entry["icon"] = it }
            entry["downloads"] = listOf(release)
            return entry
        }

        /**
         * A `PluginIndex.Release` of headlessmc-plugins for the given jar.
         */
        @JvmStatic
        internal fun release(version: String, headlessmc: String, java: Int, url: String, jar: Path): Map<String, Any> =
            linkedMapOf(
                "version" to version,
                "headlessmc" to headlessmc,
                "java" to java,
                "url" to url,
                "sha256" to NativeDownloader.sha256(jar),
                "size" to Files.size(jar),
            )
    }
}
