package io.github.headlesshq.headlessmc.gradle.dev;

import io.github.headlesshq.headlessmc.gradle.AbstractHeadlessMcTask;
import io.github.headlesshq.headlessmc.gradle.HeadlessMcExtension;
import io.github.headlesshq.headlessmc.gradle.HeadlessMcPlugin;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.tasks.Jar;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds HeadlessMc plugins.
 * <ul>
 *     <li>{@code headlessmcPlugin}: the {@link HeadlessMcPluginExtension}, describes the plugin.</li>
 *     <li>{@code headlessmc}: the HeadlessMc runtime, the plugin is compiled and tested against it. Never packaged.</li>
 *     <li>{@code include}: libraries nested into the plugin jar (Jar-in-Jar) under META-INF/jars, with
 *     their transitive dependencies, except for the ones HeadlessMc already contains.</li>
 *     <li>{@code generatePluginManifest}: writes META-INF/headlessmc-plugin.json.</li>
 *     <li>{@code installPlugin}: installs the plugin jar and the {@code headlessmcPlugins} into the HeadlessMc of
 *     the project, which lives in {@code run/headlessmc}.</li>
 *     <li>Applies the {@link HeadlessMcPlugin}, every HeadlessMc task, e.g.
 *     {@code ./gradlew headlessmc --args="plugin ls"}, runs the HeadlessMc of the project with this plugin installed.</li>
 * </ul>
 */
public class HeadlessMcDevPlugin implements Plugin<Project> {
    public static final String EXTENSION = "headlessmcPlugin";
    public static final String HEADLESSMC_CONFIGURATION = "headlessmc";
    public static final String INCLUDE_CONFIGURATION = "include";
    private static final Logger LOGGER = Logging.getLogger(HeadlessMcDevPlugin.class);

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaLibraryPlugin.class);
        project.getPluginManager().apply(HeadlessMcPlugin.class);
        HeadlessMcExtension headlessMcExtension = project.getExtensions().getByType(HeadlessMcExtension.class);
        HeadlessMcPluginExtension extension = project.getExtensions().create(EXTENSION, HeadlessMcPluginExtension.class);
        // plugins need a newer HeadlessMc than the default of the HeadlessMc plugin
        headlessMcExtension.getGroup().convention(Defaults.GROUP);
        headlessMcExtension.getVersion().convention(Defaults.VERSION);
        extension.getHeadlessmcVersion().convention(headlessMcExtension.getVersion());
        // the default HeadlessMc may be a snapshot of the next release
        extension.getHeadlessmc().convention(extension.getHeadlessmcVersion()
            .map(version -> version.equals(Defaults.VERSION) ? Defaults.COMPATIBLE : version));
        extension.getName().convention(extension.getId());
        extension.getJava().convention(GeneratePluginManifest.MIN_JAVA);
        extension.getFailOnError().convention(true);
        extension.getMutableJar().convention(project.getLayout().file(headlessMcExtension.getIncludedBuild().map(name ->
            HeadlessMcPlugin.includedApplication(project, name).resolve("build/headlessmc-mutable.zip").toFile())));
        // compile with the Java version the plugin needs, Gradle itself may run on an older Java
        project.getExtensions().getByType(JavaPluginExtension.class).getToolchain().getLanguageVersion()
            .convention(extension.getJava().map(JavaLanguageVersion::of));

        ObjectFactory objects = project.getObjects();
        Configuration headlessmc = project.getConfigurations().create(HEADLESSMC_CONFIGURATION, configuration -> {
            configuration.setCanBeConsumed(false);
            configuration.setCanBeResolved(true);
            configuration.setDescription("The HeadlessMc runtime the plugin is compiled against, never packaged.");
            configuration.attributes(attributes -> runtimeAttributes(objects, attributes));
        });
        project.getDependencies().addProvider(HEADLESSMC_CONFIGURATION,
            extension.getHeadlessmcVersion().map(version -> headlessMcExtension.getGroup().get()
                + ":" + HeadlessMcPlugin.APPLICATION_MODULE + ":" + version));
        // compile against the complete runtime classpath of HeadlessMc, it is exactly what the plugin runs with,
        // the api scope of the HeadlessMc modules misses e.g. the CDI annotations
        project.getDependencies().add("compileOnly", project.files(headlessmc));
        project.getDependencies().add("testImplementation", project.files(headlessmc));

        Configuration include = project.getConfigurations().create(INCLUDE_CONFIGURATION, configuration -> {
            configuration.setCanBeConsumed(false);
            configuration.setCanBeResolved(false);
            configuration.setDescription("Libraries to nest into the plugin jar (Jar-in-Jar).");
        });
        project.getConfigurations().named("implementation", configuration -> configuration.extendsFrom(include));
        Configuration includeClasspath = project.getConfigurations().create("includeClasspath", configuration -> {
            configuration.setCanBeConsumed(false);
            configuration.setCanBeResolved(true);
            configuration.extendsFrom(include);
            configuration.attributes(attributes -> runtimeAttributes(objects, attributes));
        });

        // group:artifact -> version of the libraries HeadlessMc contains
        Provider<Map<String, String>> coreModules = headlessmc.getIncoming().getArtifacts().getResolvedArtifacts()
            .map(HeadlessMcDevPlugin::modules);
        Provider<List<ResolvedArtifactResult>> nested = includeClasspath.getIncoming().getArtifacts().getResolvedArtifacts()
            .zip(coreModules, HeadlessMcDevPlugin::withoutCoreModules);
        Provider<List<File>> nestedFiles = nested.map(artifacts -> artifacts.stream().map(ResolvedArtifactResult::getFile).toList());

        TaskProvider<GeneratePluginManifest> manifest = project.getTasks().register("generatePluginManifest", GeneratePluginManifest.class, task -> {
            task.setGroup("build");
            task.setDescription("Generates META-INF/" + GeneratePluginManifest.FILE_NAME + ".");
            task.getPluginId().set(extension.getId());
            task.getPluginVersion().set(project.provider(() -> project.getVersion().toString()));
            task.getPluginName().set(extension.getName());
            task.getPluginDescription().set(extension.getDescription());
            task.getAuthors().set(extension.getAuthors());
            task.getLicense().set(extension.getLicense());
            task.getHomepage().set(extension.getHomepage());
            task.getHeadlessmc().set(extension.getHeadlessmc());
            task.getJava().set(extension.getJava());
            task.getDependencies().set(extension.getDependencies());
            task.getOptionalDependencies().set(extension.getOptionalDependencies());
            task.getLibraryVersions().set(extension.getLibraryVersions());
            task.getLibraries().set(nested.map(artifacts -> artifacts.stream().map(artifact -> {
                ModuleComponentIdentifier id = (ModuleComponentIdentifier) artifact.getId().getComponentIdentifier();
                return id.getGroup() + ":" + id.getModule() + ":" + id.getVersion() + ":" + artifact.getFile().getName();
            }).toList()));
            task.getLibraryFiles().from(nestedFiles);
            task.getOutput().set(project.getLayout().getBuildDirectory().file("generated/headlessmc/" + GeneratePluginManifest.FILE_NAME));
        });

        project.getTasks().named("jar", Jar.class, jar -> {
            jar.from(manifest, spec -> spec.into("META-INF"));
            jar.from(nestedFiles, spec -> spec.into(GeneratePluginManifest.JARS_DIR));
        });

        project.getTasks().withType(JavaCompile.class).configureEach(task -> {
            task.getOptions().setEncoding("UTF-8");
            task.getOptions().getRelease().convention(extension.getJava());
        });

        TaskProvider<Jar> jar = project.getTasks().named("jar", Jar.class);
        // every HeadlessMc task runs the HeadlessMc of the project with this plugin installed
        project.getTasks().withType(AbstractHeadlessMcTask.class).configureEach(task -> {
            task.getPlugins().from(jar);
            task.getLauncherConfig().put("hmc.plugins.fail-on-error", extension.getFailOnError().map(Object::toString));
            // an included build of HeadlessMc is re-built with the plugins from its own mutable jar
            task.dependsOn(headlessMcExtension.getIncludedBuild().map(name -> List.of(
                project.getGradle().includedBuild(name).task(":" + HeadlessMcPlugin.APPLICATION_MODULE + ":mutableJarZip")))
                .orElse(List.of()));
            task.getLauncherConfig().putAll(extension.getMutableJar().map(file ->
                Map.of("hmc.plugins.mutable-jar", file.getAsFile().getAbsolutePath())).orElse(Map.of()));
        });

        project.getTasks().register("installPlugin", Sync.class, task -> {
            task.setGroup(HeadlessMcPlugin.GROUP);
            task.setDescription("Installs the plugin into the HeadlessMc of the project, run it with ./gradlew headlessmc.");
            task.from(jar);
            task.from(project.getConfigurations().named(HeadlessMcPlugin.PLUGINS_CONFIGURATION));
            task.into(headlessMcExtension.getLauncherDirectory().dir("plugins"));
        });
    }

    private static Map<String, String> modules(Collection<ResolvedArtifactResult> artifacts) {
        Map<String, String> modules = new HashMap<>();
        for (ResolvedArtifactResult artifact : artifacts) {
            if (artifact.getId().getComponentIdentifier() instanceof ModuleComponentIdentifier id) {
                modules.put(id.getGroup() + ":" + id.getModule(), id.getVersion());
            }
        }

        return modules;
    }

    private static List<ResolvedArtifactResult> withoutCoreModules(Collection<ResolvedArtifactResult> artifacts,
                                                                   Map<String, String> coreModules) {
        List<ResolvedArtifactResult> result = new ArrayList<>(artifacts.size());
        for (ResolvedArtifactResult artifact : artifacts) {
            ComponentIdentifier componentId = artifact.getId().getComponentIdentifier();
            if (!(componentId instanceof ModuleComponentIdentifier id)) {
                throw new GradleException("Only external modules can be included, not " + componentId.getDisplayName());
            }

            String module = id.getGroup() + ":" + id.getModule();
            String coreVersion = coreModules.get(module);
            if (coreVersion == null) {
                result.add(artifact);
            } else {
                checkCoreVersion(module, id.getVersion(), coreVersion);
            }
        }

        return result;
    }

    /**
     * HeadlessMc always uses its own version of the libraries it contains.
     * Fails if the plugin needs a different major version, warns if it needs a newer one.
     */
    static void checkCoreVersion(String module, String version, String coreVersion) {
        if (version.equals(coreVersion)) {
            return;
        }

        if (!Versions.major(version).equals(Versions.major(coreVersion))) {
            throw new GradleException("The plugin needs " + module + ":" + version + ", but HeadlessMc ships "
                + coreVersion + " and plugins cannot replace libraries of HeadlessMc.");
        }

        if (Versions.compare(version, coreVersion) > 0) {
            LOGGER.warn("The plugin needs {}:{}, but HeadlessMc ships the older {}, which will be used at runtime.",
                module, version, coreVersion);
        }
    }

    private static void runtimeAttributes(ObjectFactory objects, AttributeContainer attributes) {
        attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_RUNTIME));
        attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.LIBRARY));
        attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.class, LibraryElements.JAR));
        attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.class, Bundling.EXTERNAL));
    }

}
