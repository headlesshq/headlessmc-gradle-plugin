package io.github.headlesshq.headlessmc.gradle.dev

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

/**
 * The `headlessmcPlugin` extension, describes the plugin.
 * Everything here ends up in `META-INF/headlessmc-plugin.json`, the version is the project version.
 */
abstract class HeadlessMcPluginExtension {
    /**
     * The HeadlessMc version to compile against, e.g. 3.0.0-RC9. Defaults to `headlessmc.version`,
     * which this plugin sets to a build of HeadlessMc supporting plugins (with `headlessmc.group`).
     */
    abstract val headlessmcVersion: Property<String>

    /**
     * The unique id of the plugin: lowercase letters, digits, - and _.
     */
    abstract val id: Property<String>

    /**
     * A human-readable name, defaults to the id.
     */
    abstract val name: Property<String>

    /**
     * A short description, shown by `headlessmc plugin ls`.
     */
    abstract val description: Property<String>

    abstract val authors: ListProperty<String>

    /**
     * The license, preferably an SPDX identifier like MIT.
     */
    abstract val license: Property<String>

    /**
     * A link to the homepage or the source code.
     */
    abstract val homepage: Property<String>

    /**
     * The range of HeadlessMc versions the plugin is compatible with, Maven range syntax, e.g. [3.0.0-RC9,4).
     * A bare version means up to the next major version. Defaults to [headlessmcVersion],
     * or the version the default HeadlessMc build will be released as.
     */
    abstract val headlessmc: Property<String>

    /**
     * The minimum Java version the plugin needs, at least and by default 25.
     */
    abstract val java: Property<Int>

    /**
     * Plugin id to the range of versions of the other plugins this plugin depends on.
     */
    abstract val dependencies: MapProperty<String, String>

    /**
     * The ids of the [dependencies] which do not need to be installed.
     */
    abstract val optionalDependencies: SetProperty<String>

    /**
     * group:artifact to the range of versions of an included library this plugin is compatible with.
     * Libraries not listed here default to [version, nextMajor).
     */
    abstract val libraryVersions: MapProperty<String, String>

    /**
     * A HeadlessMc `quarkus-app` directory or `headlessmc-mutable.zip` to build HeadlessMc with
     * the plugins from (`hmc.plugins.mutable-jar`), e.g. of a local HeadlessMc build.
     * By default, HeadlessMc downloads the one of its release.
     */
    abstract val mutableJar: RegularFileProperty

    /**
     * If HeadlessMc exits when a plugin cannot be loaded (`hmc.plugins.fail-on-error`), true by default,
     * so that a broken plugin fails the build.
     */
    abstract val failOnError: Property<Boolean>

    /**
     * Declares a required dependency on another plugin.
     *
     * @param id the id of the other plugin.
     * @param versions the range of versions of the other plugin, e.g. [1.0,2).
     */
    fun dependency(id: String, versions: String) {
        dependencies.put(id, versions)
    }

    /**
     * Declares a dependency on another plugin, which does not need to be installed.
     *
     * @param id the id of the other plugin.
     * @param versions the range of versions of the other plugin, e.g. [1.0,2).
     */
    fun optionalDependency(id: String, versions: String) {
        dependencies.put(id, versions)
        optionalDependencies.add(id)
    }

    /**
     * Overrides the range of compatible versions for an included library,
     * e.g. `libraryVersions 'com.google.guava:guava', '[32,34)'`.
     * When multiple plugins include the same library, HeadlessMc uses the highest version
     * contained in the ranges of all plugins and refuses to load them if there is none.
     *
     * @param module group:artifact of the library.
     * @param versions the range of compatible versions.
     */
    fun libraryVersions(module: String, versions: String) {
        libraryVersions.put(module, versions)
    }
}
