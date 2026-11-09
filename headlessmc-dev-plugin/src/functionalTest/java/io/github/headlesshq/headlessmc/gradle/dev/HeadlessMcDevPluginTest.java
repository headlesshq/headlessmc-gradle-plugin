package io.github.headlesshq.headlessmc.gradle.dev;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadlessMcDevPluginTest {
    private static final Path FAKE_JAR = Path.of(System.getProperty("fake.headlessmc.jar"));

    @TempDir
    Path dir;

    private Path repository;
    private Path project;

    @BeforeEach
    void setup() throws IOException {
        repository = dir.resolve("repository");
        // the real path, like Gradle's project directory, e.g. /private/var instead of /var on macOS
        project = dir.toRealPath().resolve("project");
        // HeadlessMc ships commons-lang3 3.21.0, plugins never nest it
        publish(Defaults.GROUP, "headlessmc-application", "1.0.0", "org.apache.commons:commons-lang3:3.21.0");
        publish("org.apache.commons", "commons-lang3", "3.21.0");
        publish("org.apache.commons", "commons-lang3", "3.20.0");
        publish("org.apache.commons", "commons-lang3", "4.0.0");
        publish("com.example", "lib", "1.2.3", "org.apache.commons:commons-lang3:3.20.0", "com.example:transitive:2.0.0");
        publish("com.example", "transitive", "2.0.0");
        publish("com.example", "next-major", "1.0.0", "org.apache.commons:commons-lang3:4.0.0");
        write("src/main/java/com/example/Example.java", "package com.example; public class Example {}");
        write("settings.gradle", "rootProject.name = 'example-plugin'\n");
        write("gradle.properties", "version=1.0.0\n");
    }

    @Test
    void jarNestsLibrariesAndContainsTheManifest() throws IOException {
        buildGradle("""
            headlessmcPlugin {
                headlessmcVersion = '1.0.0'
                id = 'example'
                description = 'An example plugin'
                authors = ['someone']
                license = 'MIT'
                homepage = 'https://example.com'
                dependency 'required', '[1,2)'
                optionalDependency 'optional', '*'
                libraryVersions 'com.example:lib', '[1.2,3)'
            }

            dependencies {
                include 'com.example:lib:1.2.3'
            }
            """);
        runner("jar").build();

        Path jar = project.resolve("build/libs/example-plugin-1.0.0.jar");
        JsonNode manifest;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            List<String> entries = Collections.list(zip.entries()).stream().map(ZipEntry::getName).toList();
            assertTrue(entries.contains("com/example/Example.class"), entries.toString());
            assertTrue(entries.contains("META-INF/jars/lib-1.2.3.jar"), entries.toString());
            assertTrue(entries.contains("META-INF/jars/transitive-2.0.0.jar"), entries.toString());
            assertTrue(entries.stream().noneMatch(entry -> entry.contains("commons-lang3")), entries.toString());
            try (InputStream in = zip.getInputStream(zip.getEntry("META-INF/headlessmc-plugin.json"))) {
                manifest = new ObjectMapper().readTree(in);
            }

            // compiled for Java 25, the toolchain is set by the plugin
            try (DataInputStream in = new DataInputStream(zip.getInputStream(zip.getEntry("com/example/Example.class")))) {
                assertEquals(0xCAFEBABE, in.readInt());
                in.readUnsignedShort();
                assertEquals(69, in.readUnsignedShort());
            }
        }

        assertEquals(1, manifest.get("schemaVersion").asInt());
        assertEquals("example", manifest.get("id").asText());
        assertEquals("1.0.0", manifest.get("version").asText());
        assertEquals("example", manifest.get("name").asText());
        assertEquals("An example plugin", manifest.get("description").asText());
        assertEquals("someone", manifest.get("authors").get(0).asText());
        assertEquals("MIT", manifest.get("license").asText());
        assertEquals("https://example.com", manifest.get("homepage").asText());
        assertEquals("1.0.0", manifest.get("headlessmc").asText());
        assertEquals(25, manifest.get("java").asInt());

        JsonNode dependencies = manifest.get("dependencies");
        assertEquals(2, dependencies.size());
        assertEquals("optional", dependencies.get(0).get("id").asText());
        assertEquals("*", dependencies.get(0).get("versions").asText());
        assertTrue(dependencies.get(0).get("optional").asBoolean());
        assertEquals("required", dependencies.get(1).get("id").asText());
        assertEquals("[1,2)", dependencies.get(1).get("versions").asText());
        assertFalse(dependencies.get(1).get("optional").asBoolean());

        JsonNode libraries = manifest.get("libraries");
        assertEquals(2, libraries.size());
        JsonNode lib = null;
        for (JsonNode library : libraries) {
            if (library.get("artifact").asText().equals("lib")) {
                lib = library;
            }
        }

        assertTrue(lib != null, libraries.toString());
        assertEquals("com.example", lib.get("group").asText());
        assertEquals("1.2.3", lib.get("version").asText());
        assertEquals("[1.2,3)", lib.get("versions").asText());
        assertEquals("META-INF/jars/lib-1.2.3.jar", lib.get("path").asText());
        assertEquals(io.github.headlesshq.headlessmc.gradle.internal.NativeDownloader.sha256(repository.resolve("com/example/lib/1.2.3/lib-1.2.3.jar")),
            lib.get("sha256").asText());
    }

    @Test
    void generatePluginIndexEntry() throws IOException {
        buildGradle("""
            headlessmcPlugin {
                headlessmcVersion = '1.0.0'
                id = 'example'
                name = 'Example'
                description = 'An example plugin'
                authors = ['someone']
                license = 'MIT'
                homepage = 'https://github.com/owner/example-plugin/tree/main/plugin'
                icon = 'https://example.com/icon.png'
            }

            dependencies {
                include 'com.example:lib:1.2.3'
            }
            """);
        BuildResult result = runner("generatePluginIndexEntry").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":jar").getOutcome());

        Path file = project.resolve("build/headlessmc-plugin/index-entry.json");
        JsonNode json = new ObjectMapper().readTree(file.toFile());
        assertEquals(List.of("id", "name", "description", "authors", "license", "homepage", "icon", "downloads"), fieldNames(json));
        assertEquals(componentNames(Entry.class), fieldNames(json));
        assertEquals(componentNames(Release.class), fieldNames(json.get("downloads").get(0)));

        Entry entry = new ObjectMapper().readValue(file.toFile(), Entry.class);
        assertEquals("example", entry.id());
        assertEquals("Example", entry.name());
        assertEquals("An example plugin", entry.description());
        assertEquals(List.of("someone"), entry.authors());
        assertEquals("MIT", entry.license());
        assertEquals("https://github.com/owner/example-plugin/tree/main/plugin", entry.homepage());
        assertEquals("https://example.com/icon.png", entry.icon());
        assertEquals(1, entry.downloads().size());

        Path jar = project.resolve("build/libs/example-plugin-1.0.0.jar");
        Release release = entry.downloads().get(0);
        assertEquals("1.0.0", release.version());
        assertEquals("1.0.0", release.headlessmc());
        assertEquals(25, release.java());
        assertEquals("https://github.com/owner/example-plugin/releases/download/1.0.0/example-plugin-1.0.0.jar", release.url());
        assertEquals(sha256(jar), release.sha256());
        assertTrue(release.sha256().matches("[0-9a-f]{64}"), release.sha256());
        assertEquals(Files.size(jar), release.size());

        BuildResult second = runner("generatePluginIndexEntry").build();
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":generatePluginIndexEntry").getOutcome());
    }

    @Test
    void generatePluginIndexEntryWithDownloadUrl() throws IOException {
        buildGradle("""
            headlessmcPlugin {
                headlessmcVersion = '1.0.0'
                id = 'example'
                homepage = 'https://example.com'
                downloadUrl = 'https://example.com/example-plugin-1.0.0.jar'
            }
            """);
        runner("generatePluginIndexEntry", "--configuration-cache").build();
        Entry entry = new ObjectMapper().readValue(project.resolve("build/headlessmc-plugin/index-entry.json").toFile(), Entry.class);
        assertEquals("example", entry.id());
        assertEquals("example", entry.name());
        assertEquals(null, entry.description());
        assertEquals(List.of(), entry.authors());
        assertEquals(null, entry.icon());
        assertEquals("https://example.com/example-plugin-1.0.0.jar", entry.downloads().get(0).url());
    }

    @Test
    void generatePluginIndexEntryWithoutDownloadUrlFails() throws IOException {
        buildGradle("""
            headlessmcPlugin {
                headlessmcVersion = '1.0.0'
                id = 'example'
                homepage = 'https://example.com'
            }
            """);
        BuildResult result = runner("generatePluginIndexEntry").buildAndFail();
        assertTrue(result.getOutput().contains("Cannot derive the download url of the plugin from the homepage 'https://example.com'"),
            result.getOutput());
        assertTrue(result.getOutput().contains("Set headlessmcPlugin.downloadUrl"), result.getOutput());
    }

    @Test
    void coreLibraryWithOtherMajorVersionFails() throws IOException {
        buildGradle("""
            headlessmcPlugin {
                headlessmcVersion = '1.0.0'
                id = 'example'
            }

            dependencies {
                include 'com.example:next-major:1.0.0'
            }
            """);
        BuildResult result = runner("jar").buildAndFail();
        assertTrue(result.getOutput().contains("plugins cannot replace libraries of HeadlessMc"), result.getOutput());
    }

    @Test
    void invalidIdFails() throws IOException {
        buildGradle("""
            headlessmcPlugin {
                headlessmcVersion = '1.0.0'
                id = 'Invalid Id'
            }
            """);
        BuildResult result = runner("jar").buildAndFail();
        assertTrue(result.getOutput().contains("Invalid plugin id 'Invalid Id'"), result.getOutput());
    }

    @Test
    void javaBelow25Fails() throws IOException {
        buildGradle("""
            headlessmcPlugin {
                headlessmcVersion = '1.0.0'
                id = 'example'
                java = 21
            }
            """);
        BuildResult result = runner("generatePluginManifest").buildAndFail();
        assertTrue(result.getOutput().contains("HeadlessMc requires at least Java 25"), result.getOutput());
    }

    @Test
    void optionalDependencyNeedsVersions() throws IOException {
        buildGradle("""
            headlessmcPlugin {
                headlessmcVersion = '1.0.0'
                id = 'example'
                optionalDependencies.add('other')
            }
            """);
        BuildResult result = runner("generatePluginManifest").buildAndFail();
        assertTrue(result.getOutput().contains("The optional dependency other has no versions"), result.getOutput());
    }

    @Test
    void headlessmcVersionDefaultsToTheHeadlessMcExtension() throws IOException {
        buildGradle("""
            headlessmc {
                version = '1.0.0'
            }

            headlessmcPlugin {
                id = 'example'
            }
            """);
        runner("generatePluginManifest").build();
        JsonNode manifest = new ObjectMapper().readTree(project.resolve("build/generated/headlessmc/headlessmc-plugin.json").toFile());
        assertEquals("1.0.0", manifest.get("headlessmc").asText());
    }

    @Test
    void defaultHeadlessMcVersion() throws IOException {
        buildGradle("""
            headlessmcPlugin {
                id = 'example'
            }

            tasks.register('printVersion') {
                def version = headlessmcPlugin.headlessmcVersion
                def group = headlessmc.group
                def compatible = headlessmcPlugin.headlessmc
                doLast { println "VERSION ${group.get()}:${version.get()} ${compatible.get()}" }
            }
            """);
        BuildResult result = runner("printVersion").build();
        // plugins need a newer HeadlessMc than the default of the HeadlessMc plugin
        assertTrue(result.getOutput().contains("VERSION " + Defaults.GROUP + ":" + Defaults.VERSION + " " + Defaults.COMPATIBLE),
            result.getOutput());
        assertTrue(Defaults.COMPATIBLE.startsWith("["), Defaults.COMPATIBLE);
    }

    @Test
    void installPluginInstallsIntoTheProject() throws IOException {
        write("other/other-plugin.jar", "other");
        buildGradle("""
            headlessmcPlugin {
                headlessmcVersion = '1.0.0'
                id = 'example'
            }

            dependencies {
                headlessmcPlugins files('other/other-plugin.jar')
            }
            """);
        runner("installPlugin").build();
        Path plugins = project.resolve("run/headlessmc/launcher/plugins");
        assertTrue(Files.isRegularFile(plugins.resolve("example-plugin-1.0.0.jar")));
        assertTrue(Files.isRegularFile(plugins.resolve("other-plugin.jar")));
    }

    @Test
    void headlessmcRunsWithThePluginInstalled() throws IOException {
        write("local/headlessmc-mutable.zip", "zip");
        buildGradle("""
            headlessmcPlugin {
                headlessmcVersion = '1.0.0'
                id = 'example'
                mutableJar = file('local/headlessmc-mutable.zip')
            }
            """ + fakeRuntime());
        // a stale plugin of an older build
        write("run/headlessmc/launcher/plugins/example-plugin-0.9.0.jar", "old");
        BuildResult result = runner("headlessmc", "--args=hello --banner").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":jar").getOutcome());
        Path plugins = project.resolve("run/headlessmc/launcher/plugins");
        assertTrue(Files.isRegularFile(plugins.resolve("example-plugin-1.0.0.jar")));
        assertFalse(Files.exists(plugins.resolve("example-plugin-0.9.0.jar")));

        Properties invocation = lastInvocation();
        assertEquals("hello\u001f--banner", invocation.getProperty("args"));
        assertEquals("true", invocation.getProperty("property.hmc.plugins.fail-on-error"));
        assertEquals(project.resolve("local/headlessmc-mutable.zip").toAbsolutePath().toString(),
            invocation.getProperty("property.hmc.plugins.mutable-jar"));
    }

    @Test
    void failOnErrorCanBeDisabled() throws IOException {
        buildGradle("""
            headlessmcPlugin {
                headlessmcVersion = '1.0.0'
                id = 'example'
                failOnError = false
            }
            """ + fakeRuntime());
        runner("headlessmc", "--args=plugin ls").build();
        Properties invocation = lastInvocation();
        assertEquals("false", invocation.getProperty("property.hmc.plugins.fail-on-error"));
        assertEquals(null, invocation.getProperty("property.hmc.plugins.mutable-jar"));
    }

    @Test
    void runTaskLaunchesAPlatformAddedByThePlugin() throws IOException {
        buildGradle("""
            headlessmcPlugin {
                headlessmcVersion = '1.0.0'
                id = 'quilt'
            }

            tasks.register('runQuilt', HeadlessMcRunTask) {
                minecraftVersion = '1.21.4'
                platform = 'quilt'
                headless = true
                offline = true
            }
            """ + fakeRuntime());
        runner("runQuilt").build();
        assertTrue(Files.isRegularFile(project.resolve("run/headlessmc/launcher/plugins/example-plugin-1.0.0.jar")));
        assertEquals("launch\u001f--headless\u001f--offline\u001fquilt\u001f1.21.4", lastInvocation().getProperty("args"));
    }

    @Test
    void pluginDependingOnAnotherPluginProject() throws IOException {
        write("settings.gradle", "rootProject.name = 'example-plugin'\ninclude 'base-plugin'\n");
        write("base-plugin/build.gradle", """
            plugins {
                id 'io.github.headlesshq.headlessmc-dev-plugin'
            }

            repositories {
                maven { url = uri('%s') }
            }

            version = '2.0.0'

            headlessmcPlugin {
                headlessmcVersion = '1.0.0'
                id = 'base'
            }
            """.formatted(repository.toUri()));
        write("base-plugin/src/main/java/com/example/base/Base.java", "package com.example.base; public class Base {}");
        buildGradle("""
            headlessmcPlugin {
                headlessmcVersion = '1.0.0'
                id = 'example'
                dependency 'base', '[2,3)'
            }

            dependencies {
                compileOnly project(':base-plugin')
                headlessmcPlugins project(':base-plugin')
            }
            """ + fakeRuntime());
        runner(":headlessmc", "--args=plugin ls").build();
        Path plugins = project.resolve("run/headlessmc/launcher/plugins");
        assertTrue(Files.isRegularFile(plugins.resolve("example-plugin-1.0.0.jar")));
        assertTrue(Files.isRegularFile(plugins.resolve("base-plugin-2.0.0.jar")));
        try (ZipFile zip = new ZipFile(plugins.resolve("base-plugin-2.0.0.jar").toFile())) {
            assertTrue(zip.getEntry("META-INF/headlessmc-plugin.json") != null);
        }
    }

    @Test
    void configurationCache() throws IOException {
        buildGradle("""
            headlessmcPlugin {
                headlessmcVersion = '1.0.0'
                id = 'example'
            }

            dependencies {
                include 'com.example:lib:1.2.3'
            }
            """ + fakeRuntime());
        BuildResult first = runner("headlessmc", "--args=plugin ls", "--configuration-cache").build();
        assertTrue(first.getOutput().contains("Configuration cache entry stored"), first.getOutput());
        BuildResult second = runner("headlessmc", "--args=plugin ls", "--configuration-cache").build();
        assertTrue(second.getOutput().contains("Configuration cache entry reused"), second.getOutput());
    }

    @Test
    void includedHeadlessMcBuild() throws IOException {
        // a fake checkout of the headlessmc repository, e.g. a git submodule
        write("headlessmc/settings.gradle", "rootProject.name = 'headlessmc'\ninclude 'headlessmc-application'\n");
        write("headlessmc/headlessmc-application/build.gradle", """
            plugins { id 'java-library' }
            group = 'io.github.headlesshq'
            version = '3.0.0-RC8'
            repositories { maven { url = uri('%s') } }
            dependencies { api 'org.apache.commons:commons-lang3:3.21.0' }
            def applicationVersion = providers.gradleProperty('applicationVersion').getOrElse('none')
            tasks.register('quarkusBuild', Copy) {
                inputs.property('applicationVersion', applicationVersion)
                from('%s') { rename { 'quarkus-run.jar' } }
                into(layout.buildDirectory.dir('quarkus-app'))
            }
            if (providers.gradleProperty('mutableJar').present) {
                tasks.register('mutableJarZip', Zip) {
                    from('build.gradle')
                    archiveFileName = 'headlessmc-mutable.zip'
                    destinationDirectory = layout.buildDirectory
                }
            }
            """.formatted(repository.toUri(), FAKE_JAR.toAbsolutePath().toString().replace("\\", "/")));
        write("headlessmc/headlessmc-application/src/main/java/io/github/headlesshq/headlessmc/Api.java",
            "package io.github.headlesshq.headlessmc; public class Api {}");
        write("src/main/java/com/example/Example.java",
            "package com.example; public class Example extends io.github.headlesshq.headlessmc.Api {}");
        write("settings.gradle", """
            rootProject.name = 'example-plugin'
            // the included build needs the mutable jar and reports the version the plugin is compatible with
            gradle.startParameter.projectProperties += [mutableJar: 'true', applicationVersion: '3.0.0-RC9-SNAPSHOT']
            includeBuild('headlessmc')
            """);
        buildGradle("""
            headlessmc {
                includedBuild = 'headlessmc'
                distribution = HmcDistribution.JVM
            }

            headlessmcPlugin {
                id = 'example'
            }
            """);
        BuildResult result = runner("headlessmc", "--args=plugin ls").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":headlessmc:headlessmc-application:quarkusBuild").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":headlessmc:headlessmc-application:mutableJarZip").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileJava").getOutcome());
        Path application = project.resolve("headlessmc/headlessmc-application/build");
        assertTrue(Files.isRegularFile(application.resolve("quarkus-app/quarkus-run.jar")));

        Properties invocation = lastInvocation();
        assertEquals("plugin\u001fls", invocation.getProperty("args"));
        assertEquals(application.resolve("headlessmc-mutable.zip").toAbsolutePath().toString(),
            invocation.getProperty("property.hmc.plugins.mutable-jar"));
        try (ZipFile zip = new ZipFile(project.resolve("build/libs/example-plugin-1.0.0.jar").toFile());
             InputStream in = zip.getInputStream(zip.getEntry("META-INF/headlessmc-plugin.json"))) {
            assertEquals(Defaults.COMPATIBLE, new ObjectMapper().readTree(in).get("headlessmc").asText());
        }
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static List<String> componentNames(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    private static String sha256(Path file) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * A copy of {@code PluginIndex.Entry} of headlessmc-plugins (headlessmc feat/plugins).
     */
    record Entry(
        String id,
        String name,
        String description,
        List<String> authors,
        String license,
        String homepage,
        String icon,
        List<Release> downloads
    ) {
    }

    /**
     * A copy of {@code PluginIndex.Release} of headlessmc-plugins (headlessmc feat/plugins).
     */
    record Release(String version, String headlessmc, Integer java, String url, String sha256, Long size) {
    }

    private static String fakeRuntime() {
        return "\ndependencies { headlessmcRuntime files('" + FAKE_JAR.toAbsolutePath().toString().replace("\\", "/") + "') }\n"
            + "headlessmc { distribution = HmcDistribution.JVM }\n";
    }

    private Properties lastInvocation() throws IOException {
        Path invocations = project.resolve("run/headlessmc/launcher/fake-invocations");
        List<Path> files;
        try (Stream<Path> stream = Files.list(invocations)) {
            files = stream.sorted().collect(Collectors.toCollection(ArrayList::new));
        }

        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(files.get(files.size() - 1))) {
            properties.load(in);
        }

        return properties;
    }

    private void write(String path, String content) throws IOException {
        Path file = project.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private GradleRunner runner(String... arguments) {
        return GradleRunner.create()
            .withProjectDir(project.toFile())
            .withPluginClasspath()
            .forwardOutput()
            .withArguments(Stream.concat(Stream.of(arguments), Stream.of("--stacktrace", "--warning-mode=fail")).toList());
    }

    private void buildGradle(String configuration) throws IOException {
        write("build.gradle", """
            plugins {
                id 'io.github.headlesshq.headlessmc-dev-plugin'
            }

            repositories {
                maven { url = uri('%s') }
            }

            """.formatted(repository.toUri()) + configuration);
    }

    private void publish(String group, String artifact, String version, String... dependencies) throws IOException {
        Path directory = repository.resolve(group.replace('.', '/')).resolve(artifact).resolve(version);
        Files.createDirectories(directory);
        StringBuilder pom = new StringBuilder("""
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>%s</groupId>
              <artifactId>%s</artifactId>
              <version>%s</version>
              <dependencies>
            """.formatted(group, artifact, version));
        for (String dependency : dependencies) {
            String[] parts = dependency.split(":");
            pom.append("""
                    <dependency><groupId>%s</groupId><artifactId>%s</artifactId><version>%s</version></dependency>
                """.formatted(parts[0], parts[1], parts[2]));
        }

        pom.append("  </dependencies>\n</project>\n");
        Files.writeString(directory.resolve(artifact + "-" + version + ".pom"), pom.toString());
        try (OutputStream outputStream = Files.newOutputStream(directory.resolve(artifact + "-" + version + ".jar"));
             ZipOutputStream zip = new ZipOutputStream(outputStream)) {
            zip.putNextEntry(new ZipEntry(artifact + ".txt"));
            zip.write((group + ":" + artifact + ":" + version).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

}
