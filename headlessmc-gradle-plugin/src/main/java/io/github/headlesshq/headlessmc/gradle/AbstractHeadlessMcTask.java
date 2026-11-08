package io.github.headlesshq.headlessmc.gradle;

import io.github.headlesshq.headlessmc.gradle.internal.NativeDownloader;
import io.github.headlesshq.headlessmc.gradle.internal.NativePlatform;
import io.github.headlesshq.headlessmc.gradle.internal.ProjectScope;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;
import org.gradle.work.DisableCachingByDefault;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Base class for tasks that run HeadlessMc.
 * HeadlessMc is started as a separate process, either on a Java toolchain or as native executable,
 * and is configured to only place files inside the project.
 * The properties are set from the {@link HeadlessMcExtension} by default.
 */
@DisableCachingByDefault(because = "Runs HeadlessMc, which has side effects outside of Gradle's knowledge")
public abstract class AbstractHeadlessMcTask extends DefaultTask {
    @Input
    public abstract Property<String> getHeadlessMcVersion();

    /**
     * @return the Maven group of HeadlessMc, only used for messages, the jar is {@link #getHeadlessMcJar()}.
     */
    @Internal
    public abstract Property<String> getHeadlessMcGroup();

    @Input
    public abstract Property<HmcDistribution> getDistribution();

    /**
     * @return the Java HeadlessMc runs on with {@link HmcDistribution#JVM}.
     */
    @Nested
    @Optional
    public abstract Property<JavaLauncher> getJavaLauncher();

    /**
     * @return the HeadlessMc runner jar, only resolved when HeadlessMc runs on the JVM.
     */
    @Internal
    public abstract ConfigurableFileCollection getHeadlessMcJar();

    /**
     * @return JVM arguments for HeadlessMc with {@link HmcDistribution#JVM}.
     */
    @Input
    public abstract ListProperty<String> getJvmArgs();

    /**
     * @return the root directory of the build, all directories have to be inside it.
     */
    @Internal
    public abstract DirectoryProperty getScopeDirectory();

    /**
     * @return the directory HeadlessMc runs in.
     */
    @Internal
    public abstract DirectoryProperty getWorkingDirectory();

    /**
     * @return {@code hmc.files.location}, see {@link HeadlessMcExtension#getLauncherDirectory()}.
     */
    @Internal
    public abstract DirectoryProperty getLauncherDirectory();

    /**
     * @return {@code hmc.files.mc}, see {@link HeadlessMcExtension#getMcDirectory()}.
     */
    @Internal
    public abstract DirectoryProperty getMcDirectory();

    /**
     * @return {@code hmc.files.game}, the directory the client runs in.
     * If not set HeadlessMc uses a directory for each version in the {@link #getMcDirectory()}.
     */
    @Internal
    public abstract DirectoryProperty getGameDirectory();

    @Internal
    public abstract DirectoryProperty getNativeDirectory();

    /**
     * @return a native executable to use, see {@link HeadlessMcExtension#getExecutable()}.
     */
    @Internal
    public abstract RegularFileProperty getExecutable();

    @Input
    public abstract Property<String> getNativeDownloadUrl();

    @Input
    @Optional
    public abstract Property<String> getNativeSha256();

    /**
     * @return HeadlessMc configuration, passed as system properties. Extends the extension's launcherConfig.
     */
    @Input
    public abstract MapProperty<String, String> getLauncherConfig();

    @Input
    public abstract Property<Boolean> getJline();

    /**
     * @return Java installations HeadlessMc may use for the game ({@code hmc.java.versions}).
     * HeadlessMc also finds installed Java versions itself and can download missing ones into the launcher directory.
     */
    @Nested
    public abstract ListProperty<JavaLauncher> getGameJavaLaunchers();

    /**
     * @return HeadlessMc plugin jars, synchronized into the plugins directory if {@link #getManagePlugins()}.
     */
    @Classpath
    public abstract ConfigurableFileCollection getPlugins();

    @Input
    public abstract Property<Boolean> getManagePlugins();

    @Input
    public abstract Property<Boolean> getIgnoreExitValue();

    /**
     * @return if Gradle runs with --offline.
     */
    @Internal
    public abstract Property<Boolean> getGradleOffline();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    private transient @Nullable ResolvedRuntime runtime;

    private record ResolvedRuntime(List<String> command, boolean jvm) {
    }

    /**
     * Checks that all directories are inside the project.
     *
     * @return the project scope.
     */
    protected ProjectScope checkScope() {
        ProjectScope scope = new ProjectScope(getScopeDirectory().get().getAsFile());
        scope.check("launcherDirectory", getLauncherDirectory().get().getAsFile());
        scope.check("mcDirectory", getMcDirectory().get().getAsFile());
        if (getGameDirectory().isPresent()) {
            scope.check("gameDirectory", getGameDirectory().get().getAsFile());
        }

        if (!getExecutable().isPresent()) {
            scope.check("nativeDirectory", getNativeDirectory().get().getAsFile());
        }

        ProjectScope.checkConfig("launcherConfig of " + getPath(), getLauncherConfig().get());
        ProjectScope.checkJvmArgs("jvmArgs of " + getPath(), getJvmArgs().get());
        return scope;
    }

    /**
     * Synchronizes {@link #getPlugins()} into the HeadlessMc plugins directory, if {@link #getManagePlugins()}.
     */
    protected void syncPlugins() {
        if (getManagePlugins().get()) {
            File pluginsDir = getLauncherDirectory().dir("plugins").get().getAsFile();
            getFileSystemOperations().sync(spec -> {
                spec.from(getPlugins());
                spec.into(pluginsDir);
            });
        }
    }

    /**
     * Checks the scope, synchronizes the plugins and runs HeadlessMc once.
     *
     * @param args the arguments for HeadlessMc.
     * @return the result of the process.
     */
    protected ExecResult prepareAndRun(List<String> args) {
        checkScope();
        syncPlugins();
        return runHeadlessMc(args);
    }

    /**
     * Runs HeadlessMc with the given arguments.
     *
     * @param args the arguments for HeadlessMc, e.g. {@code [launch, fabric, 1.21.4]}.
     * @return the result of the process.
     */
    protected ExecResult runHeadlessMc(List<String> args) {
        ResolvedRuntime runtime = getRuntime();
        List<String> commandLine = new ArrayList<>(runtime.command());
        boolean jvm = runtime.jvm();
        List<String> systemProperties = getSystemProperties().entrySet().stream()
            .map(entry -> "-D" + entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.toList());
        // with the JVM the system properties are JVM arguments,
        // the native executable parses them from the start of its arguments
        commandLine.addAll(jvm ? commandLine.size() - 2 : commandLine.size(), systemProperties);
        commandLine.addAll(args);

        getLogger().lifecycle("[HeadlessMc] {}", String.join(" ", args));
        getLogger().info("[HeadlessMc] Command line: {}", commandLine);
        ExecResult result = getExecOperations().exec(spec -> {
            spec.setCommandLine(commandLine);
            spec.setWorkingDir(getWorkingDirectory().get().getAsFile());
            spec.setStandardInput(System.in);
            spec.setIgnoreExitValue(true);
        });

        if (result.getExitValue() != 0 && !getIgnoreExitValue().get()) {
            throw new GradleException("HeadlessMc exited with exit code " + result.getExitValue()
                + " for '" + String.join(" ", args) + "', see " + getLauncherDirectory().dir("logs").get().getAsFile());
        }

        return result;
    }

    /**
     * @return the system properties configuring HeadlessMc, the managed hmc.files.* keys come last.
     */
    @Internal
    protected Map<String, String> getSystemProperties() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("hmc.jline.enabled", getJline().get().toString());
        List<String> javaHomes = getGameJavaLaunchers().get().stream()
            .map(launcher -> launcher.getMetadata().getInstallationPath().getAsFile().getAbsolutePath())
            .distinct()
            .collect(Collectors.toList());
        if (!javaHomes.isEmpty()) {
            properties.put("hmc.java.versions", String.join(",", javaHomes));
        }

        properties.putAll(getLauncherConfig().get());
        properties.put("hmc.files.location", getLauncherDirectory().get().getAsFile().getAbsolutePath());
        properties.put("hmc.files.mc", getMcDirectory().get().getAsFile().getAbsolutePath());
        if (getGameDirectory().isPresent()) {
            properties.put("hmc.files.game", getGameDirectory().get().getAsFile().getAbsolutePath());
        }

        return properties;
    }

    /**
     * @return the command to start HeadlessMc, without arguments and system properties:
     * {@code [java, jvmArgs..., -jar, headlessmc.jar]} or {@code [executable]}.
     */
    @Internal
    protected List<String> getRuntimeCommand() {
        return getRuntime().command();
    }

    /**
     * @return if HeadlessMc runs on the JVM, otherwise the native executable is used.
     */
    @Internal
    protected boolean isRunningOnJvm() {
        return getRuntime().jvm();
    }

    private ResolvedRuntime getRuntime() {
        ResolvedRuntime runtime = this.runtime;
        if (runtime == null) {
            runtime = resolveRuntime();
            this.runtime = runtime;
        }

        return runtime;
    }

    private ResolvedRuntime resolveRuntime() {
        HmcDistribution distribution = getDistribution().get();
        if (getExecutable().isPresent()) {
            File executable = getExecutable().get().getAsFile();
            if (!executable.isFile()) {
                throw new GradleException("The HeadlessMc executable " + executable + " does not exist");
            }

            if (distribution == HmcDistribution.JVM) {
                throw new GradleException("headlessmc.executable is set, but distribution is JVM");
            }

            return new ResolvedRuntime(List.of(executable.getAbsolutePath()), false);
        }

        String jvmProblem = null;
        if (distribution != HmcDistribution.NATIVE) {
            JavaLauncher launcher = getJavaLauncher().getOrNull();
            File jar = null;
            if (launcher == null) {
                jvmProblem = "no Java toolchain for HeadlessMc was found, HeadlessMc requires Java 25."
                    + " Install Java 25, configure toolchain auto-provisioning"
                    + " (e.g. the org.gradle.toolchains.foojay-resolver-convention settings plugin)"
                    + " or set headlessmc.javaLauncher";
            } else if (launcher.getMetadata().getLanguageVersion().asInt() < 25) {
                jvmProblem = "HeadlessMc requires Java 25, but the Java launcher is Java "
                    + launcher.getMetadata().getLanguageVersion();
            } else {
                Set<File> files = getHeadlessMcJar().getFiles();
                if (files.size() != 1) {
                    String group = getHeadlessMcGroup().getOrElse(HeadlessMcPlugin.DEFAULT_GROUP);
                    jvmProblem = "the HeadlessMc jar " + group + ":" + HeadlessMcPlugin.APPLICATION_MODULE + ":"
                        + getHeadlessMcVersion().get() + ":runner could not be resolved (found " + files + ")."
                        + (group.startsWith("com.github.")
                            ? " Add maven { url = 'https://jitpack.io' } to the repositories"
                            : " Add mavenCentral() to the repositories");
                } else {
                    jar = files.iterator().next();
                }
            }

            if (jar != null) {
                List<String> command = new ArrayList<>();
                command.add(launcher.getExecutablePath().getAsFile().getAbsolutePath());
                command.addAll(getJvmArgs().get());
                command.add("-jar");
                command.add(jar.getAbsolutePath());
                return new ResolvedRuntime(Collections.unmodifiableList(command), true);
            }

            if (distribution == HmcDistribution.JVM) {
                throw new GradleException("Cannot run HeadlessMc on the JVM: " + jvmProblem);
            }
        }

        if (!getHeadlessMcGroup().getOrElse(HeadlessMcPlugin.DEFAULT_GROUP).equals(HeadlessMcPlugin.DEFAULT_GROUP)) {
            throw new GradleException("Cannot run HeadlessMc " + getHeadlessMcGroup().get() + ":" + getHeadlessMcVersion().get()
                + " natively, there are only native executables of releases"
                + (jvmProblem == null ? ", use distribution JVM" : ", and cannot run it on the JVM: " + jvmProblem));
        }

        NativePlatform platform = NativePlatform.current();
        if (platform == null) {
            throw new GradleException("HeadlessMc has no native executable for " + System.getProperty("os.name")
                + "/" + System.getProperty("os.arch") + (jvmProblem == null ? "" : " and " + jvmProblem));
        }

        if (jvmProblem != null) {
            getLogger().info("[HeadlessMc] Using the native executable, since {}", jvmProblem);
        }

        String version = getHeadlessMcVersion().get();
        Path executable = getNativeDirectory().get().getAsFile().toPath().resolve(version).resolve(platform.getAssetName());
        new NativeDownloader(getLogger()).ensure(executable, getNativeDownloadUrl().get(), version, platform,
            getNativeSha256().getOrNull(), getGradleOffline().get());
        return new ResolvedRuntime(List.of(executable.toAbsolutePath().toString()), false);
    }

}
