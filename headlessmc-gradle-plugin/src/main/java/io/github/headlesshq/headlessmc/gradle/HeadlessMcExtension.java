package io.github.headlesshq.headlessmc.gradle;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.jvm.toolchain.JavaLauncher;

/**
 * The {@code headlessmc} extension, configures how HeadlessMc is run for all HeadlessMc tasks of the project.
 * <p>
 * Everything HeadlessMc writes is placed in {@link #getDirectory()}, by default {@code run/headlessmc}.
 * Directories outside the build are rejected, so HeadlessMc never uses e.g. {@code ~/.minecraft}.
 */
public abstract class HeadlessMcExtension {
    /**
     * @return the HeadlessMc version, e.g. {@code 3.0.0-RC8}.
     */
    public abstract Property<String> getVersion();

    /**
     * @return the Maven group of HeadlessMc, {@code io.github.headlesshq} (Maven Central) by default.
     * Other groups, e.g. {@code com.github.headlesshq.headlessmc} for <a href="https://jitpack.io">JitPack</a> builds,
     * have no native executables.
     */
    public abstract Property<String> getGroup();

    /**
     * @return the name of an included build of HeadlessMc to run instead of a published HeadlessMc,
     * e.g. a git submodule of the headlessmc repository added with {@code includeBuild('headlessmc')} in the settings.
     * Its {@code headlessmc-application} is built with {@code quarkusBuild} before every HeadlessMc task
     * and runs on the JVM, dependencies on {@code io.github.headlesshq} modules are substituted by its projects.
     */
    public abstract Property<String> getIncludedBuild();

    /**
     * @return how HeadlessMc is run, {@link HmcDistribution#AUTO} by default.
     */
    public abstract Property<HmcDistribution> getDistribution();

    /**
     * @return the Java version HeadlessMc runs on with {@link HmcDistribution#JVM}, 25 by default.
     */
    public abstract Property<Integer> getJavaVersion();

    /**
     * @return the Java launcher HeadlessMc runs on with {@link HmcDistribution#JVM}.
     * Defaults to the toolchain for {@link #getJavaVersion()}, if one is available.
     */
    public abstract Property<JavaLauncher> getJavaLauncher();

    /**
     * @return JVM arguments for HeadlessMc itself (not the game) with {@link HmcDistribution#JVM}.
     */
    public abstract ListProperty<String> getJvmArgs();

    /**
     * @return the directory all HeadlessMc files are placed in, by default {@code <project>/run/headlessmc}.
     */
    public abstract DirectoryProperty getDirectory();

    /**
     * @return the HeadlessMc config, data, state and cache directory ({@code hmc.files.location}).
     * Contains config.properties, logs, accounts, downloaded Java versions, plugins and servers.
     * Defaults to {@code <directory>/launcher}.
     */
    public abstract DirectoryProperty getLauncherDirectory();

    /**
     * @return the Minecraft directory with versions, libraries and assets ({@code hmc.files.mc}).
     * Defaults to {@code <directory>/mc}.
     */
    public abstract DirectoryProperty getMcDirectory();

    /**
     * @return the directory native executables are downloaded to, defaults to {@code <directory>/native}.
     */
    public abstract DirectoryProperty getNativeDirectory();

    /**
     * @return a native HeadlessMc executable to use instead of downloading it, e.g. a local build.
     * Setting it implies {@link HmcDistribution#NATIVE}.
     */
    public abstract RegularFileProperty getExecutable();

    /**
     * @return the url native executables are downloaded from, {@code <url>/<version>/<executable>}.
     */
    public abstract Property<String> getNativeDownloadUrl();

    /**
     * @return the SHA-256 hash of the native executable for this machine.
     * If not set, the hash is taken from the GitHub release.
     */
    public abstract Property<String> getNativeSha256();

    /**
     * @return HeadlessMc configuration for all tasks, e.g. {@code hmc.assets.dummy=true}.
     * The {@code hmc.files.*} keys are managed by the plugin and cannot be set.
     */
    public abstract MapProperty<String, String> getLauncherConfig();

    /**
     * @return if HeadlessMc may use JLine, false by default, since JLine does not work well inside Gradle.
     */
    public abstract Property<Boolean> getJline();

    /**
     * @return if the HeadlessMc plugins directory is synchronized with the {@code headlessmcPlugins} configuration
     * before every task. True by default.
     */
    public abstract Property<Boolean> getManagePlugins();

}
