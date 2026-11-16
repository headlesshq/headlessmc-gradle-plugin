package io.github.headlesshq.headlessmc.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Base class for TestKit tests, which run HeadlessMc tasks against the fake HeadlessMc.
 */
abstract class FunctionalTestBase {
    static final Path FAKE_JAR = Paths.get(System.getProperty("fake.headlessmc.jar"));

    @TempDir
    Path tempDir;

    /**
     * The real path of the temporary directory, like Gradle's project directory,
     * e.g. /private/var instead of /var on macOS, without short names on Windows.
     */
    Path projectDir;

    @org.junit.jupiter.api.BeforeEach
    void resolveProjectDir() throws IOException {
        projectDir = tempDir.toRealPath();
    }

    Path file(String path) {
        return projectDir.resolve(path);
    }

    void write(String path, String content) {
        try {
            Path file = file(path);
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Writes settings.gradle and a build.gradle which applies the plugin and runs the fake HeadlessMc on the JVM.
     */
    void groovyBuild(String build) {
        write("settings.gradle", "rootProject.name = 'test-project'\n");
        write("build.gradle", "plugins {\n    id 'io.github.headlesshq.headlessmc'\n}\n\n"
            + fakeRuntime() + "\n" + build);
    }

    void kotlinBuild(String build) {
        write("settings.gradle.kts", "rootProject.name = \"test-project\"\n");
        write("build.gradle.kts", "plugins {\n    id(\"io.github.headlesshq.headlessmc\")\n}\n\n"
            + "dependencies {\n    headlessmcRuntime(files(\"" + path(FAKE_JAR) + "\"))\n}\n\n"
            + "headlessmc {\n    distribution.set(io.github.headlesshq.headlessmc.gradle.HmcDistribution.JVM)\n}\n\n" + build);
    }

    static String fakeRuntime() {
        return "dependencies {\n    headlessmcRuntime files('" + path(FAKE_JAR) + "')\n}\n\n"
            + "headlessmc {\n    distribution = HmcDistribution.JVM\n}\n";
    }

    static String realPath(Path path) {
        try {
            return path.toRealPath().toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String path(Path path) {
        return path.toAbsolutePath().toString().replace("\\", "/");
    }

    GradleRunner runner(String... args) {
        List<String> arguments = new ArrayList<>(Arrays.asList(args));
        arguments.add("--stacktrace");
        return GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments(arguments)
            .forwardOutput();
    }

    BuildResult build(String... args) {
        return runner(args).build();
    }

    BuildResult buildAndFail(String... args) {
        return runner(args).buildAndFail();
    }

    Path launcherDir() {
        return file("run/headlessmc/launcher");
    }

    /**
     * @return the invocations of the fake HeadlessMc, in order.
     */
    List<Invocation> invocations() {
        return invocations(launcherDir());
    }

    static List<Invocation> invocations(Path launcherDir) {
        Path dir = launcherDir.resolve("fake-invocations");
        if (!Files.isDirectory(dir)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.list(dir)) {
            return stream.sorted().map(Invocation::read).collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    record Invocation(Properties properties) {
        static Invocation read(Path file) {
            try (InputStream in = Files.newInputStream(file)) {
                Properties properties = new Properties();
                properties.load(in);
                return new Invocation(properties);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        List<String> args() {
            if (properties.getProperty("argCount").equals("0")) {
                return List.of();
            }

            return Arrays.asList(properties.getProperty("args").split("\u001f", -1));
        }

        String property(String key) {
            return properties.getProperty("property." + key);
        }

        String mode() {
            return properties.getProperty("mode");
        }

        String javaVersion() {
            return properties.getProperty("java.specification.version");
        }

        String cwd() {
            return properties.getProperty("cwd");
        }

        String parsedProperties() {
            return properties.getProperty("parsedProperties");
        }
    }

}
