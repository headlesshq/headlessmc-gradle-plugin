package io.github.headlesshq.headlessmc.gradle;

import io.github.headlesshq.headlessmc.gradle.internal.NativeDownloader;
import io.github.headlesshq.headlessmc.gradle.internal.PluginInfo;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.Directory;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.plugins.JvmToolchainsPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;

import java.util.List;

/**
 * The HeadlessMc Gradle plugin.
 * <ul>
 *     <li>{@code headlessmc}: the {@link HeadlessMcExtension}.</li>
 *     <li>{@code headlessmcRuntime}: the HeadlessMc runner jar, by default
 *     {@code io.github.headlesshq:headlessmc-application:<version>:runner}.</li>
 *     <li>{@code headlessmcPlugins}: HeadlessMc plugin jars, installed into the project's HeadlessMc.</li>
 *     <li>{@code headlessmc} task: a {@link HeadlessMcExec}, {@code ./gradlew headlessmc --args="..."}.</li>
 * </ul>
 * Register {@link HeadlessMcRunTask}s to launch the game.
 */
public class HeadlessMcPlugin implements Plugin<Project> {
    public static final String EXTENSION = "headlessmc";
    public static final String RUNTIME_CONFIGURATION = "headlessmcRuntime";
    public static final String PLUGINS_CONFIGURATION = "headlessmcPlugins";
    public static final String EXEC_TASK = "headlessmc";
    public static final String GROUP = "headlessmc";
    public static final String DEFAULT_GROUP = "io.github.headlesshq";
    public static final String JITPACK_GROUP = "com.github.headlesshq.headlessmc";
    public static final String APPLICATION_MODULE = "headlessmc-application";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JvmToolchainsPlugin.class);
        Logger logger = project.getLogger();
        HeadlessMcExtension extension = project.getExtensions().create(EXTENSION, HeadlessMcExtension.class);
        extension.getVersion().convention(PluginInfo.getHeadlessMcVersion());
        extension.getGroup().convention(DEFAULT_GROUP);
        extension.getDistribution().convention(HmcDistribution.AUTO);
        extension.getJavaVersion().convention(25);
        extension.getDirectory().convention(project.getLayout().getProjectDirectory().dir("run").dir("headlessmc"));
        extension.getLauncherDirectory().convention(extension.getDirectory().dir("launcher"));
        extension.getMcDirectory().convention(extension.getDirectory().dir("mc"));
        extension.getNativeDirectory().convention(extension.getDirectory().dir("native"));
        extension.getNativeDownloadUrl().convention(NativeDownloader.GITHUB_RELEASES);
        extension.getJline().convention(false);
        extension.getManagePlugins().convention(true);

        JavaToolchainService toolchains = project.getExtensions().getByType(JavaToolchainService.class);
        // AUTO falls back to the native executable, so a missing toolchain must not fail the build
        Provider<JavaLauncher> toolchain = project.getProviders().provider(() -> {
            try {
                return toolchains.launcherFor(spec -> spec.getLanguageVersion()
                    .set(extension.getJavaVersion().map(JavaLanguageVersion::of))).getOrNull();
            } catch (RuntimeException e) {
                logger.info("[HeadlessMc] No Java {} toolchain found: {}", extension.getJavaVersion().get(), e.toString());
                return null;
            }
        });
        extension.getJavaLauncher().convention(toolchain);

        Configuration runtime = project.getConfigurations().create(RUNTIME_CONFIGURATION, configuration -> {
            configuration.setCanBeConsumed(false);
            configuration.setCanBeResolved(true);
            configuration.setTransitive(false);
            configuration.setDescription("The HeadlessMc jar, run with Java " + 25 + ".");
            configuration.defaultDependencies(dependencies -> dependencies.add(extension.getIncludedBuild().isPresent()
                ? project.getDependencies().create(project.files(includedApplication(project, extension.getIncludedBuild().get())
                    .resolve("build/quarkus-app/quarkus-run.jar")))
                : project.getDependencies().create(
                    extension.getGroup().get() + ":" + APPLICATION_MODULE + ":" + extension.getVersion().get() + ":runner")));
        });

        Configuration plugins = project.getConfigurations().create(PLUGINS_CONFIGURATION, configuration -> {
            configuration.setCanBeConsumed(false);
            configuration.setCanBeResolved(true);
            // plugin jars nest their libraries
            configuration.setTransitive(false);
            configuration.setDescription("HeadlessMc plugins, installed into the HeadlessMc of this project.");
        });

        Directory rootDirectory = project.getRootProject().getLayout().getProjectDirectory();
        Provider<Boolean> offline = project.getProviders().provider(() -> project.getGradle().getStartParameter().isOffline());
        project.getTasks().withType(AbstractHeadlessMcTask.class).configureEach(task -> {
            task.setGroup(GROUP);
            task.getHeadlessMcVersion().convention(extension.getVersion());
            task.getHeadlessMcGroup().convention(extension.getGroup());
            task.dependsOn(extension.getIncludedBuild().map(name -> List.of(
                project.getGradle().includedBuild(name).task(":" + APPLICATION_MODULE + ":quarkusBuild"))).orElse(List.of()));
            task.getDistribution().convention(extension.getDistribution());
            task.getJavaLauncher().convention(extension.getJavaLauncher());
            // lenient, so that a missing repository only fails if HeadlessMc actually runs on the JVM
            task.getHeadlessMcJar().from(task.getDistribution().map(distribution -> distribution == HmcDistribution.NATIVE
                ? project.files()
                : runtime.getIncoming().artifactView(view -> view.setLenient(true)).getFiles()));
            task.getJvmArgs().convention(extension.getJvmArgs());
            task.getScopeDirectory().convention(rootDirectory);
            task.getWorkingDirectory().convention(project.getLayout().getProjectDirectory());
            task.getLauncherDirectory().convention(extension.getLauncherDirectory());
            task.getMcDirectory().convention(extension.getMcDirectory());
            task.getNativeDirectory().convention(extension.getNativeDirectory());
            task.getExecutable().convention(extension.getExecutable());
            task.getNativeDownloadUrl().convention(extension.getNativeDownloadUrl());
            task.getNativeSha256().convention(extension.getNativeSha256());
            task.getLauncherConfig().putAll(extension.getLauncherConfig());
            task.getJline().convention(extension.getJline());
            task.getPlugins().from(plugins);
            task.getManagePlugins().convention(extension.getManagePlugins());
            task.getIgnoreExitValue().convention(false);
            task.getGradleOffline().convention(offline);
        });

        project.getTasks().withType(HeadlessMcRunTask.class).configureEach(task -> {
            task.setDescription("Launches Minecraft with HeadlessMc.");
            task.getPlatform().convention(HmcPlatform.VANILLA);
            task.getServer().convention(false);
            task.getHeadless().convention(false);
            task.getOffline().convention(false);
            task.getDummyAssets().convention(false);
            task.getAcceptEula().convention(false);
            task.getCleanDestination().convention(true);
            task.getGameDirectory().convention(extension.getDirectory().dir("game").map(dir -> dir.dir(task.getName())));
        });

        project.getTasks().register(EXEC_TASK, HeadlessMcExec.class, task ->
            task.setDescription("Runs HeadlessMc, e.g. ./gradlew headlessmc --args=\"version list\"."));

        // so that build.gradle files can use the types without imports
        ExtraPropertiesExtension extra = project.getExtensions().getExtraProperties();
        extra.set(HeadlessMcRunTask.class.getSimpleName(), HeadlessMcRunTask.class);
        extra.set(HeadlessMcExec.class.getSimpleName(), HeadlessMcExec.class);
        extra.set(HmcDistribution.class.getSimpleName(), HmcDistribution.class);
        extra.set(HmcPlatform.class.getSimpleName(), HmcPlatform.class);
    }

    /**
     * @param project the project.
     * @param includedBuild the name of an included build of HeadlessMc.
     * @return the directory of the headlessmc-application project of the included build.
     */
    public static java.nio.file.Path includedApplication(Project project, String includedBuild) {
        return project.getGradle().includedBuild(includedBuild).getProjectDir().toPath().resolve(APPLICATION_MODULE);
    }

}
