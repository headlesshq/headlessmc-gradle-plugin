package io.github.headlesshq.headlessmc.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectScopeFunctionalTest extends FunctionalTestBase {
    private void assertRejected(String build, String expected) {
        groovyBuild(build + "\ntasks.register('runClient', HeadlessMcRunTask) { minecraftVersion = '1.21.4' }\n");
        BuildResult result = buildAndFail("runClient");
        assertTrue(result.getOutput().contains(expected), result.getOutput());
        assertTrue(invocations().isEmpty(), "HeadlessMc must not run");
    }

    @Test
    void mcDirectoryOutsideTheProjectIsRejected() {
        assertRejected("headlessmc { mcDirectory = file(System.getProperty('user.home') + '/.minecraft') }",
            "mcDirectory is");
    }

    @Test
    void launcherDirectoryOutsideTheProjectIsRejected() {
        assertRejected("headlessmc { launcherDirectory = layout.projectDirectory.dir('../outside') }",
            "launcherDirectory is");
        assertFalse(Files.exists(projectDir.resolveSibling("outside")));
    }

    @Test
    void directoryOutsideTheProjectIsRejected() {
        assertRejected("headlessmc { directory = file(System.getProperty('java.io.tmpdir')) }", "may only use directories inside the build");
    }

    @Test
    void gameDirectoryOutsideTheProjectIsRejected() {
        groovyBuild("tasks.register('runClient', HeadlessMcRunTask) {\n"
            + "    minecraftVersion = '1.21.4'\n"
            + "    gameDirectory = file(System.getProperty('user.home') + '/.minecraft')\n"
            + "}\n");
        BuildResult result = buildAndFail("runClient");
        assertTrue(result.getOutput().contains("gameDirectory is"), result.getOutput());
    }

    @Test
    void destinationOutsideTheProjectIsRejected() {
        write("mods/a.jar", "a");
        groovyBuild("tasks.register('runClient', HeadlessMcRunTask) {\n"
            + "    from('mods')\n"
            + "    into(file('../escape'))\n"
            + "    minecraftVersion = '1.21.4'\n"
            + "}\n");
        BuildResult result = buildAndFail("runClient");
        assertTrue(result.getOutput().contains("destinationDirectory is"), result.getOutput());
        assertFalse(Files.exists(projectDir.resolveSibling("escape")));
    }

    @Test
    void managedConfigCannotBeOverridden() {
        assertRejected("headlessmc { launcherConfig.put('hmc.files.location', '/tmp/hmc') }", "hmc.files.location");
    }

    @Test
    void managedSystemPropertiesCannotBeSetAsJvmArgs() {
        assertRejected("headlessmc { jvmArgs.add('-Dhmc.files.mc=/tmp/mc') }", "hmc.files.mc");
    }

    @Test
    void legacyKeysAreRejectedToo() {
        assertRejected("headlessmc { launcherConfig.put('hmc.mcdir', '/tmp/mc') }", "hmc.mcdir");
    }

    @Test
    void customDirectoriesInsideTheProjectWork() {
        groovyBuild("headlessmc {\n"
            + "    directory = layout.projectDirectory.dir('.hmc')\n"
            + "    mcDirectory = layout.projectDirectory.dir('minecraft')\n"
            + "}\n"
            + "tasks.register('runClient', HeadlessMcRunTask) { minecraftVersion = '1.21.4' }\n");
        build("runClient");
        Invocation invocation = invocations(file(".hmc/launcher")).get(0);
        assertEquals(file("minecraft").toAbsolutePath().toString(), invocation.property("hmc.files.mc"));
        assertEquals(file(".hmc/game/runClient").toAbsolutePath().toString(), invocation.property("hmc.files.game"));
    }

    @Test
    void subprojectsMayShareADirectoryInTheRootProject() {
        write("settings.gradle", "rootProject.name = 'root'\ninclude 'fabric', 'neoforge'\n");
        write("build.gradle", "");
        String sub = "plugins { id 'io.github.headlesshq.headlessmc' }\n" + fakeRuntime()
            + "headlessmc { directory = rootProject.layout.projectDirectory.dir('run/headlessmc') }\n"
            + "tasks.register('runClient', HeadlessMcRunTask) { minecraftVersion = '1.21.4'; platform = project.name }\n";
        write("fabric/build.gradle", sub);
        write("neoforge/build.gradle", sub);
        build("runClient", "--no-parallel");
        assertEquals(2, invocations().size());
        assertTrue(Files.isDirectory(file("run/headlessmc/game/runClient")) || true);
    }

}
