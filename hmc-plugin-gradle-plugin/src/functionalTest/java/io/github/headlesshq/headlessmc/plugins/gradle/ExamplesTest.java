package io.github.headlesshq.headlessmc.plugins.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Configures and runs the examples which do not need third-party plugins, with the fake HeadlessMc.
 * The examples use includeBuild('../..'), which is replaced by the TestKit plugin classpath.
 */
class ExamplesTest {
    private static final Path EXAMPLES = Path.of(System.getProperty("examples.dir"));
    private static final Path FAKE_JAR = Path.of(System.getProperty("fake.headlessmc.jar"));

    @TempDir
    Path dir;

    private Path copy(String example) throws IOException {
        Path source = EXAMPLES.resolve(example);
        Path target = dir.resolve(example);
        try (Stream<Path> files = Files.walk(source)) {
            for (Path file : files.collect(Collectors.toList())) {
                Path relative = source.relativize(file);
                if (relative.startsWith("run") || relative.startsWith("build") || relative.startsWith(".gradle")) {
                    continue;
                }

                Path destination = target.resolve(relative.toString());
                if (Files.isDirectory(file)) {
                    Files.createDirectories(destination);
                } else {
                    String name = file.getFileName().toString();
                    if (name.startsWith("settings.gradle")) {
                        String settings = Files.readString(file).lines()
                            .filter(line -> !line.contains("includeBuild("))
                            .collect(Collectors.joining("\n"));
                        Files.writeString(destination, settings + "\n");
                    } else {
                        Files.copy(file, destination);
                    }
                }
            }
        }

        // replaces HeadlessMc with the fake, without the example knowing about it
        Files.writeString(target.resolve("fake.init.gradle"), """
            allprojects {
                plugins.withId('io.github.headlesshq.headlessmc-gradle-plugin') {
                    dependencies { headlessmcRuntime files('%s') }
                    def distribution = headlessmc.distribution
                    distribution.set(distribution.type.enumConstants.find { it.name() == 'JVM' })
                    headlessmc.executable.set((File) null)
                }
            }
            """.formatted(FAKE_JAR.toAbsolutePath().toString().replace("\\", "/")));
        return target;
    }

    private static GradleRunner runner(Path project, String... args) {
        List<String> arguments = new ArrayList<>(Arrays.asList(args));
        arguments.add("--init-script=fake.init.gradle");
        arguments.add("--stacktrace");
        return GradleRunner.create()
            .withProjectDir(project.toFile())
            .withPluginClasspath()
            .withArguments(arguments)
            .forwardOutput();
    }

    private static List<List<String>> invocations(Path launcherDir) throws IOException {
        Path invocations = launcherDir.resolve("fake-invocations");
        try (Stream<Path> files = Files.list(invocations)) {
            return files.sorted().map(file -> {
                Properties properties = new Properties();
                try (InputStream in = Files.newInputStream(file)) {
                    properties.load(in);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }

                return properties.getProperty("argCount").equals("0")
                    ? List.<String>of()
                    : Arrays.asList(properties.getProperty("args").split("\u001f", -1));
            }).collect(Collectors.toList());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "vanilla-kotlin", "version-matrix", "paper-server", "commands-and-config", "native-executable",
        "local-headlessmc", "hmc-plugin", "multi-project"
    })
    void examplesConfigure(String example) throws IOException {
        Path project = copy(example);
        BuildResult result = runner(project, "tasks", "--all", "--configuration-cache").build();
        assertTrue(result.getOutput().contains("Headlessmc tasks"), result.getOutput());
    }

    @Test
    void vanillaKotlin() throws IOException {
        Path project = copy("vanilla-kotlin");
        runner(project, "runVanilla").build();
        assertEquals(List.of(List.of("launch", "--headless", "--offline",
                "--jvm", "\"-Xmx2G\" \"-Dsome.property=a value with spaces\"", "1.21.4")),
            invocations(project.resolve("run/headlessmc/launcher")));
        assertTrue(Files.readString(project.resolve("run/headlessmc/game/runVanilla/options.txt")).contains("pauseOnLostFocus:false"));
    }

    @Test
    void versionMatrix() throws IOException {
        Path project = copy("version-matrix");
        runner(project, "runAll").build();
        List<List<String>> invocations = invocations(project.resolve("run/headlessmc/launcher"));
        List<List<String>> launches = invocations.stream().filter(args -> args.get(0).equals("launch")).toList();
        assertEquals(List.of(
            List.of("launch", "--headless", "--offline", "fabric", "1.20.4"),
            List.of("launch", "--headless", "--offline", "fabric", "1.21.1"),
            List.of("launch", "--headless", "--offline", "fabric", "1.21.4"),
            List.of("launch", "--headless", "--offline", "neoforge", "1.21.1"),
            List.of("launch", "--headless", "--offline", "forge", "1.20.1")
        ), launches);
        assertTrue(invocations.contains(List.of("mod", "add", "mod", "fabric-api", "fabric", "1.21.4")));
        assertTrue(invocations.contains(List.of("mod", "add", "mod", "mc-runtime-test", "forge", "1.20.1")));
        assertTrue(Files.isRegularFile(project.resolve("run/headlessmc/game/runForge1_20_1/mods/version-matrix-1.0.0.jar")));
    }

    @Test
    void paperServer() throws IOException {
        Path project = copy("paper-server");
        runner(project, "runPaper").build();
        Path launcher = project.resolve("run/headlessmc/launcher");
        assertEquals(List.of(List.of("launch", "--eula-accept", "--jvm", "\"-Xmx2G\"", "server", "paper", "1.21.4")),
            invocations(launcher));
        assertTrue(Files.isRegularFile(launcher.resolve("server/server-server-paper-1.21.4/plugins/paper-server-1.0.0.jar")));
    }

    @Test
    void commandsAndConfig() throws IOException {
        Path project = copy("commands-and-config");
        runner(project, "downloadVersions", "runConfigured", "headlessmc", "--args=version list").build();
        List<List<String>> invocations = invocations(project.resolve("run/headlessmc/launcher"));
        assertEquals(List.of("download", "1.21.4"), invocations.get(0));
        // first the Modrinth projects, then the commands
        assertEquals(List.of("mod", "add", "mod", "fabric-api", "fabric", "1.21.4"), invocations.get(1));
        assertEquals(List.of("mod", "add", "mod", "sodium", "fabric", "1.21.4"), invocations.get(2));
        assertEquals(List.of("java", "list"), invocations.get(3));
        assertEquals(List.of("version", "list"), invocations.get(4));
        assertEquals("launch", invocations.get(5).get(0));
        assertEquals(List.of("version", "list"), invocations.get(6));
        assertTrue(Files.readString(project.resolve("run/headlessmc/game/shared/options.txt")).contains("renderDistance:4"));
    }

    @Test
    void localHeadlessMc() throws IOException {
        Path project = copy("local-headlessmc");
        runner(project, "runLocal", "-PheadlessmcJar=" + FAKE_JAR.toAbsolutePath()).build();
        assertEquals(List.of(List.of("launch", "--headless", "--offline", "1.21.4")),
            invocations(project.resolve("run/headlessmc/launcher")));
    }

}
