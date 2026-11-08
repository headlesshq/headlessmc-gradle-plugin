package io.github.headlesshq.headlessmc.gradle;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginsTest extends FunctionalTestBase {
    @Test
    void headlessmcPluginsAreInstalledIntoTheProjectsHeadlessMc() {
        write("plugins/quilt.jar", "quilt");
        write("run/headlessmc/launcher/plugins/stale.jar", "stale");
        groovyBuild("dependencies { headlessmcPlugins files('plugins/quilt.jar') }\n");
        build("headlessmc", "--args=plugin ls");
        assertTrue(Files.isRegularFile(launcherDir().resolve("plugins/quilt.jar")));
        assertFalse(Files.exists(launcherDir().resolve("plugins/stale.jar")), "the plugins directory is managed");
    }

    @Test
    void pluginsCanBeManagedManually() {
        write("run/headlessmc/launcher/plugins/manual.jar", "manual");
        groovyBuild("headlessmc { managePlugins = false }\n");
        build("headlessmc", "--args=plugin ls");
        assertTrue(Files.exists(launcherDir().resolve("plugins/manual.jar")));
    }

    @Test
    void runTasksInstallPluginsToo() {
        write("plugins/quilt.jar", "quilt");
        groovyBuild("dependencies { headlessmcPlugins files('plugins/quilt.jar') }\n"
            + "tasks.register('runQuilt', HeadlessMcRunTask) {\n"
            + "    minecraftVersion = '1.21.4'\n"
            + "    platform = 'quilt'\n"
            + "}\n");
        build("runQuilt");
        assertTrue(Files.isRegularFile(launcherDir().resolve("plugins/quilt.jar")));
    }

}
