package io.github.headlesshq.headlessmc.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the real HeadlessMc. Needs network access, run with ./gradlew integrationTest.
 */
class RealHeadlessMcTest {
    @TempDir
    Path projectDir;

    private void write(String path, String content) throws IOException {
        Path file = projectDir.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private void build(String distribution, String extra) throws IOException {
        write("settings.gradle", "rootProject.name = 'it'\n");
        write("build.gradle", "plugins { id 'io.github.headlesshq.headlessmc' }\n"
            + "repositories { mavenCentral() }\n"
            + "headlessmc {\n"
            + "    version = '" + System.getProperty("headlessmc.version") + "'\n"
            + "    distribution = HmcDistribution." + distribution + "\n"
            + "}\n" + extra);
    }

    private GradleRunner runner(String... args) {
        return GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments(args)
            .forwardOutput();
    }

    @ParameterizedTest
    @ValueSource(strings = {"JVM", "NATIVE"})
    void debugShowsProjectDirectories(String distribution) throws IOException {
        build(distribution, "");
        BuildResult result = runner("headlessmc", "--args=debug").build();
        String output = result.getOutput();
        Path launcher = projectDir.toRealPath().resolve("run/headlessmc/launcher");
        assertTrue(output.contains("HeadlessMc - " + System.getProperty("headlessmc.version")), output);
        assertTrue(output.contains("Data:   " + launcher), output);
        assertTrue(output.contains("Config: " + launcher), output);
        assertTrue(output.contains("State:  " + launcher), output);
        assertTrue(output.contains("Cache:  " + launcher.resolve("cache")), output);
        assertTrue(output.contains("Mc:     " + projectDir.toRealPath().resolve("run/headlessmc/mc")), output);
        assertTrue(Files.isRegularFile(launcher.resolve("config.properties")));
        if (distribution.equals("NATIVE")) {
            try (Stream<Path> files = Files.list(projectDir.resolve("run/headlessmc/native"))) {
                assertTrue(files.findAny().isPresent());
            }
        } else {
            assertFalse(Files.exists(projectDir.resolve("run/headlessmc/native")));
        }
    }

    @Test
    void configShowsOverrides() throws IOException {
        build("AUTO", "headlessmc { launcherConfig.put('hmc.assets.dummy', 'true') }\n");
        String output = runner("headlessmc", "--args=config ls").build().getOutput();
        assertTrue(output.matches("(?s).*hmc\\.assets\\.dummy\\s+true.*"), output);
        assertTrue(output.contains(projectDir.toRealPath().resolve("run/headlessmc/launcher").toString()), output);
    }

    @Test
    void failingCommandFailsTheBuild() throws IOException {
        build("AUTO", "");
        BuildResult result = runner("headlessmc", "--args=this-command-does-not-exist").buildAndFail();
        assertTrue(result.getOutput().contains("HeadlessMc exited with exit code"), result.getOutput());
    }

    /**
     * Downloads and launches Minecraft headless with mc-runtime-test, which joins a world and quits.
     * Takes a while, enable with HMC_IT_LAUNCH=true.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "HMC_IT_LAUNCH", matches = "true")
    void launchesFabricHeadless() throws IOException {
        build("AUTO", "tasks.register('runClient', HeadlessMcRunTask) {\n"
            + "    minecraftVersion = '1.21.4'\n"
            + "    platform = HmcPlatform.FABRIC\n"
            + "    headless = true\n"
            + "    offline = true\n"
            + "    dummyAssets = true\n"
            + "    modrinth 'fabric-api', 'mc-runtime-test'\n"
            + "    ciOptions()\n"
            + "}\n");
        runner("runClient").build();
        assertTrue(Files.isDirectory(projectDir.resolve("run/headlessmc/game/runClient/mods")));
    }

}
