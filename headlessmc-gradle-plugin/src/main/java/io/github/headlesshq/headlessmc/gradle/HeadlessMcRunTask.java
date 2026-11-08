package io.github.headlesshq.headlessmc.gradle;

import groovy.lang.Closure;
import io.github.headlesshq.headlessmc.gradle.internal.Args;
import io.github.headlesshq.headlessmc.gradle.internal.LaunchArgs;
import io.github.headlesshq.headlessmc.gradle.internal.MinecraftOptions;
import io.github.headlesshq.headlessmc.gradle.internal.ProjectScope;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Transformer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.CopySourceSpec;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Launches Minecraft with HeadlessMc.
 * <p>
 * The task works like a {@link org.gradle.api.tasks.Copy} task:
 * before launching, everything configured with {@link #from(Object...)} is copied {@link #into(Object)}
 * the destination, by default the {@link #getModsDirectory() mods directory} of the game.
 * <pre>
 * tasks.register('runFabric', HeadlessMcRunTask) {
 *     from(remapJar)
 *     into(modsDirectory)
 *     minecraftVersion = '1.21.4'
 *     platform = 'fabric'
 *     headless = true
 *     offline = true
 * }
 * </pre>
 * The task launches {@code headlessmc launch [options] [server] [platform] <version> [loaderVersion]},
 * after running {@link #getModrinth() mod add} for every Modrinth project and every {@link #getCommands() command}.
 */
@DisableCachingByDefault(because = "Launches Minecraft")
public abstract class HeadlessMcRunTask extends AbstractHeadlessMcTask implements CopySourceSpec {
    private final CopySpec mods;

    @SuppressWarnings("this-escape")
    public HeadlessMcRunTask() {
        this.mods = getProject().copySpec();
        getDestinationDirectory().convention(getModsDirectory());
    }

    /**
     * @return the Minecraft version to launch, e.g. {@code 1.21.4}. Required, unless {@link #getLaunchArgs()} is set.
     */
    @Input
    @Optional
    public abstract Property<String> getMinecraftVersion();

    /**
     * @return the platform (mod loader) to launch, see {@link HmcPlatform}, vanilla by default.
     */
    @Input
    public abstract Property<String> getPlatform();

    /**
     * @return the version of the platform, e.g. the Fabric loader version {@code 0.16.14}. Latest by default.
     * Uses the build names of HeadlessMc, e.g. {@code 265} for NeoForge {@code 21.1.265},
     * see {@code ./gradlew headlessmc --args="version list --remote <platform> <version>"}.
     */
    @Input
    @Optional
    public abstract Property<String> getLoaderVersion();

    /**
     * @return if a server is launched instead of the client.
     */
    @Input
    public abstract Property<Boolean> getServer();

    /**
     * @return if the client runs headless, HeadlessMc patches LWJGL to not render anything ({@code --headless}).
     */
    @Input
    public abstract Property<Boolean> getHeadless();

    /**
     * @return if an offline account is used ({@code --offline}), for CI. Offline accounts force headless mode
     * if no account has been logged in.
     */
    @Input
    public abstract Property<Boolean> getOffline();

    /**
     * @return if dummy assets are used instead of downloading the real assets ({@code hmc.assets.dummy}).
     */
    @Input
    public abstract Property<Boolean> getDummyAssets();

    /**
     * @return if the server EULA is accepted automatically ({@code --eula-accept}).
     */
    @Input
    public abstract Property<Boolean> getAcceptEula();

    /**
     * @return arguments for the JVM the game runs in ({@code --jvm}).
     */
    @Input
    public abstract ListProperty<String> getGameJvmArgs();

    /**
     * @return arguments for the game ({@code --game}).
     */
    @Input
    public abstract ListProperty<String> getGameArgs();

    /**
     * @return a server the client joins after starting ({@code --server}).
     */
    @Input
    @Optional
    public abstract Property<String> getJoinServer();

    /**
     * @return the resolution of the client, e.g. {@code 800x600} ({@code --resolution}).
     */
    @Input
    @Optional
    public abstract Property<String> getResolution();

    /**
     * @return how often launching is retried ({@code --retries}).
     */
    @Input
    @Optional
    public abstract Property<Integer> getRetries();

    /**
     * @return the patchers HeadlessMc uses ({@code --patchers}).
     */
    @Input
    public abstract ListProperty<String> getPatchers();

    /**
     * @return additional arguments for the launch command.
     */
    @Input
    public abstract ListProperty<String> getExtraLaunchArgs();

    /**
     * @return replaces the arguments of the launch, if set, e.g. {@code ['launch', '--packwiz', 'pack', 'client']}.
     */
    @Input
    public abstract ListProperty<String> getLaunchArgs();

    /**
     * @return ids of Modrinth projects to add with {@code mod add mod <id> <platform> <version>} before launching,
     * e.g. {@code fabric-api} or {@code mc-runtime-test}. For servers with a {@link #getLoaderVersion()},
     * HeadlessMc needs the server to be installed already, e.g. by launching it once.
     */
    @Input
    public abstract ListProperty<String> getModrinth();

    /**
     * @return HeadlessMc commands run before launching, each one a separate invocation,
     * e.g. {@code "java list"}. Quotes group arguments.
     */
    @Input
    public abstract ListProperty<String> getCommands();

    /**
     * @return options merged into the {@code options.txt} of the game directory before launching,
     * e.g. {@code pauseOnLostFocus: false}.
     */
    @Input
    public abstract MapProperty<String, String> getMinecraftOptions();

    /**
     * @return if the destination directory is emptied before copying, so old versions of a mod do not remain.
     * True by default.
     */
    @Input
    public abstract Property<Boolean> getCleanDestination();

    /**
     * @return the directory the files configured with {@link #from(Object...)} are copied into.
     * Defaults to the {@link #getModsDirectory()}.
     */
    @Internal
    public abstract DirectoryProperty getDestinationDirectory();

    /**
     * @return all sources of {@link #from(Object...)}, for task dependencies.
     */
    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public abstract ConfigurableFileCollection getSource();

    /**
     * @return the directory the game runs in. For the client this is the {@link #getGameDirectory()},
     * for servers HeadlessMc uses a directory in {@code <launcherDirectory>/server}.
     */
    @Internal
    public Provider<Directory> getRunDirectory() {
        return getServer().flatMap(server -> server
            ? getLauncherDirectory().dir("server").map(dir -> dir.dir(toLaunchArgs().serverDirectoryName()))
            : getGameDirectory());
    }

    /**
     * @return the {@code mods} directory inside the {@link #getRunDirectory()}.
     */
    @Internal
    public Provider<Directory> getModsDirectory() {
        return getRunDirectory().map(dir -> dir.dir("mods"));
    }

    /**
     * @return the {@code plugins} directory of a server, e.g. for Paper plugins. Not the HeadlessMc plugins.
     */
    @Internal
    public Provider<Directory> getServerPluginsDirectory() {
        return getRunDirectory().map(dir -> dir.dir("plugins"));
    }

    /**
     * @return the copy spec used to copy the files, e.g. to configure duplicate handling.
     */
    @Internal
    public CopySpec getCopySpec() {
        return mods;
    }

    /**
     * Configures the {@link #getCopySpec() copy spec}.
     *
     * @param action configures the spec.
     */
    public void copySpec(Action<? super CopySpec> action) {
        action.execute(mods);
    }

    @Override
    public HeadlessMcRunTask from(Object... sourcePaths) {
        getSource().from(sourcePaths);
        mods.from(sourcePaths);
        return this;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public HeadlessMcRunTask from(Object sourcePath, Closure configureClosure) {
        getSource().from(sourcePath);
        mods.from(sourcePath, configureClosure);
        return this;
    }

    @Override
    public HeadlessMcRunTask from(Object sourcePath, Action<? super CopySpec> configureAction) {
        getSource().from(sourcePath);
        mods.from(sourcePath, configureAction);
        return this;
    }

    /**
     * Sets the {@link #getDestinationDirectory()}, like {@link org.gradle.api.tasks.Copy#into(Object)}.
     *
     * @param destination a directory, file or provider of one, e.g. {@code modsDirectory}.
     * @return this task.
     */
    public HeadlessMcRunTask into(Object destination) {
        File projectDir = getProject().getProjectDir();
        if (destination instanceof Provider<?>) {
            getDestinationDirectory().fileProvider(((Provider<?>) destination).map(value -> toFile(projectDir, value)));
        } else {
            getDestinationDirectory().set(toFile(projectDir, destination));
        }

        return this;
    }

    /**
     * Copies into a directory relative to the {@link #getDestinationDirectory()}.
     *
     * @param destination the relative directory.
     * @param action configures the files to copy into it.
     * @return this task.
     */
    public HeadlessMcRunTask into(Object destination, Action<? super CopySpec> action) {
        mods.into(destination, action);
        return this;
    }

    private static File toFile(File projectDir, Object value) {
        Object resolved = value;
        if (resolved instanceof Directory) {
            resolved = ((Directory) resolved).getAsFile();
        } else if (resolved instanceof java.nio.file.Path) {
            resolved = ((java.nio.file.Path) resolved).toFile();
        }

        File file = resolved instanceof File ? (File) resolved : new File(resolved.toString());
        return file.isAbsolute() ? file : new File(projectDir, file.getPath());
    }

    /**
     * @param includes Ant-style include patterns.
     * @return this task.
     */
    public HeadlessMcRunTask include(String... includes) {
        mods.include(includes);
        return this;
    }

    /**
     * @param excludes Ant-style exclude patterns.
     * @return this task.
     */
    public HeadlessMcRunTask exclude(String... excludes) {
        mods.exclude(excludes);
        return this;
    }

    /**
     * @param renamer renames copied files, returns null to keep the name.
     * @return this task.
     */
    public HeadlessMcRunTask rename(Transformer<@Nullable String, String> renamer) {
        mods.rename(renamer);
        return this;
    }

    /**
     * @param sourceRegEx a regular expression for file names.
     * @param replaceWith the replacement, may contain groups like {@code $1}.
     * @return this task.
     */
    public HeadlessMcRunTask rename(String sourceRegEx, String replaceWith) {
        mods.rename(sourceRegEx, replaceWith);
        return this;
    }

    /**
     * @param action called for each copied file.
     * @return this task.
     */
    public HeadlessMcRunTask eachFile(Action<? super FileCopyDetails> action) {
        mods.eachFile(action);
        return this;
    }

    /**
     * @param strategy how duplicate files are handled.
     */
    public void setDuplicatesStrategy(DuplicatesStrategy strategy) {
        mods.setDuplicatesStrategy(strategy);
    }

    /**
     * Adds Modrinth projects, see {@link #getModrinth()}.
     *
     * @param ids the project ids.
     */
    public void modrinth(String... ids) {
        getModrinth().addAll(ids);
    }

    /**
     * Adds a HeadlessMc command to run before launching, see {@link #getCommands()}.
     *
     * @param command the command, e.g. {@code "java list"}.
     */
    public void command(String command) {
        getCommands().add(command);
    }

    /**
     * Sets {@code pauseOnLostFocus:false}, {@code onboardAccessibility:false} and {@code narrator:0},
     * recommended for running the client headless.
     */
    public void ciOptions() {
        getMinecraftOptions().put("pauseOnLostFocus", "false");
        getMinecraftOptions().put("onboardAccessibility", "false");
        getMinecraftOptions().put("narrator", "0");
    }

    /**
     * @return the launch arguments built from the properties of this task.
     */
    protected LaunchArgs toLaunchArgs() {
        return new LaunchArgs(
            getServer().get(),
            getPlatform().get(),
            getMinecraftVersion().get(),
            getLoaderVersion().getOrNull(),
            getHeadless().get(),
            getOffline().get(),
            getAcceptEula().get(),
            getGameJvmArgs().get(),
            getGameArgs().get(),
            getJoinServer().getOrNull(),
            getResolution().getOrNull(),
            getRetries().getOrNull(),
            getPatchers().get(),
            getExtraLaunchArgs().get()
        );
    }

    @Override
    @Internal
    protected Map<String, String> getSystemProperties() {
        Map<String, String> properties = super.getSystemProperties();
        if (getDummyAssets().get()) {
            properties.putIfAbsent("hmc.assets.dummy", "true");
        }

        if (getServer().get()) {
            // HeadlessMc ignores hmc.files.game for servers
            properties.remove("hmc.files.game");
        }

        return properties;
    }

    @TaskAction
    public void launch() {
        if (!getMinecraftVersion().isPresent() && getLaunchArgs().get().isEmpty()) {
            throw new GradleException("Set minecraftVersion on " + getPath());
        }

        ProjectScope scope = checkScope();
        boolean copies = !getSource().isEmpty();
        File destination = null;
        if (copies) {
            destination = scope.check("destinationDirectory", getDestinationDirectory().get().getAsFile()).toFile();
        }

        File runDirectory = getMinecraftVersion().isPresent() ? getRunDirectory().get().getAsFile() : null;
        if (runDirectory != null) {
            scope.check("runDirectory", runDirectory);
        }

        syncPlugins();
        if (destination != null) {
            copyFiles(destination);
        }

        if (runDirectory != null && !getMinecraftOptions().get().isEmpty()) {
            try {
                MinecraftOptions.merge(runDirectory.toPath().resolve("options.txt"), getMinecraftOptions().get());
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to write options.txt", e);
            }
        }

        LaunchArgs launchArgs = getMinecraftVersion().isPresent() ? toLaunchArgs() : null;
        for (String modrinth : getModrinth().get()) {
            if (launchArgs == null) {
                throw new GradleException("modrinth needs minecraftVersion to be set on " + getPath());
            }

            List<String> args = new ArrayList<>(List.of("mod", "add", "mod", modrinth));
            args.addAll(launchArgs.modVersionArgs());
            runHeadlessMc(args);
        }

        for (String command : getCommands().get()) {
            runHeadlessMc(Args.split(command));
        }

        List<String> args = getLaunchArgs().get();
        runHeadlessMc(args.isEmpty() ? launchArgs.toArgs() : args);
    }

    private void copyFiles(File destination) {
        if (getCleanDestination().get() && destination.isDirectory()) {
            getFileSystemOperations().delete(spec -> spec.delete((Object[]) destination.listFiles()));
        }

        getFileSystemOperations().copy(spec -> {
            spec.into(destination);
            spec.with(mods);
        });
    }

}
