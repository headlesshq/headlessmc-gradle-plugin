package io.github.headlesshq.headlessmc.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadlessMcRunTaskTest extends FunctionalTestBase {
    private static final String JAVA_PROJECT = "apply plugin: 'java'\n"
        + "version = '1.0.0'\n"
        + "base { archivesName = 'mymod' }\n";

    @Test
    void copiesTheModJarIntoTheModsDirectoryAndLaunches() throws Exception {
        write("src/main/java/com/example/Mod.java", "package com.example; public class Mod {}");
        groovyBuild(JAVA_PROJECT
            + "tasks.register('runFabric', HeadlessMcRunTask) {\n"
            + "    from(jar)\n"
            + "    into(modsDirectory)\n"
            + "    minecraftVersion = '1.21.4'\n"
            + "    platform = HmcPlatform.FABRIC\n"
            + "}\n");
        BuildResult result = build("runFabric");
        // the jar task is inferred from from(jar)
        assertEquals(TaskOutcome.SUCCESS, result.task(":jar").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":runFabric").getOutcome());
        Path mods = file("run/headlessmc/game/runFabric/mods");
        assertTrue(Files.isRegularFile(mods.resolve("mymod-1.0.0.jar")));
        assertTrue(result.getOutput().contains("[FakeHeadlessMc] mod mymod-1.0.0.jar"), result.getOutput());

        List<Invocation> invocations = invocations();
        assertEquals(1, invocations.size());
        assertEquals(List.of("launch", "fabric", "1.21.4"), invocations.get(0).args());
        assertEquals(projectDir.resolve("run/headlessmc/game/runFabric").toAbsolutePath().toString(),
            invocations.get(0).property("hmc.files.game"));
    }

    @Test
    void intoIsOptionalAndDefaultsToTheModsDirectory() {
        write("mods/a.jar", "a");
        groovyBuild("tasks.register('runClient', HeadlessMcRunTask) {\n"
            + "    from('mods')\n"
            + "    minecraftVersion = '1.21.4'\n"
            + "}\n");
        build("runClient");
        assertTrue(Files.isRegularFile(file("run/headlessmc/game/runClient/mods/a.jar")));
    }

    @Test
    void oldModsAreRemoved() throws Exception {
        write("mods/new.jar", "new");
        write("run/headlessmc/game/runClient/mods/old.jar", "old");
        groovyBuild("tasks.register('runClient', HeadlessMcRunTask) {\n"
            + "    from('mods')\n"
            + "    into(modsDirectory)\n"
            + "    minecraftVersion = '1.21.4'\n"
            + "}\n");
        build("runClient");
        assertFalse(Files.exists(file("run/headlessmc/game/runClient/mods/old.jar")));
        assertTrue(Files.exists(file("run/headlessmc/game/runClient/mods/new.jar")));
    }

    @Test
    void oldModsCanBeKept() {
        write("mods/new.jar", "new");
        write("run/headlessmc/game/runClient/mods/old.jar", "old");
        groovyBuild("tasks.register('runClient', HeadlessMcRunTask) {\n"
            + "    from('mods')\n"
            + "    into(modsDirectory)\n"
            + "    cleanDestination = false\n"
            + "    minecraftVersion = '1.21.4'\n"
            + "}\n");
        build("runClient");
        assertTrue(Files.exists(file("run/headlessmc/game/runClient/mods/old.jar")));
        assertTrue(Files.exists(file("run/headlessmc/game/runClient/mods/new.jar")));
    }

    @Test
    void withoutSourcesTheModsDirectoryIsNotTouched() {
        write("run/headlessmc/game/runClient/mods/manual.jar", "manual");
        groovyBuild("tasks.register('runClient', HeadlessMcRunTask) {\n"
            + "    minecraftVersion = '1.21.4'\n"
            + "}\n");
        build("runClient");
        assertTrue(Files.exists(file("run/headlessmc/game/runClient/mods/manual.jar")));
    }

    @Test
    void copySpecFeatures() {
        write("libs/fabric-api-0.100.0.jar", "api");
        write("libs/other-1.0.jar", "other");
        write("config/mymod.json", "{}");
        groovyBuild("tasks.register('runClient', HeadlessMcRunTask) {\n"
            + "    from('libs') {\n"
            + "        include 'fabric-api-*.jar'\n"
            + "        rename { 'fabric-api.jar' }\n"
            + "    }\n"
            + "    into(modsDirectory)\n"
            + "    into('../config') {\n"
            + "        from('config')\n"
            + "    }\n"
            + "    minecraftVersion = '1.21.4'\n"
            + "}\n");
        build("runClient");
        assertTrue(Files.isRegularFile(file("run/headlessmc/game/runClient/mods/fabric-api.jar")));
        assertFalse(Files.exists(file("run/headlessmc/game/runClient/mods/other-1.0.jar")));
        assertTrue(Files.isRegularFile(file("run/headlessmc/game/runClient/config/mymod.json")));
    }

    @Test
    void allLaunchOptions() {
        groovyBuild("tasks.register('runClient', HeadlessMcRunTask) {\n"
            + "    minecraftVersion = '1.21.1'\n"
            + "    platform = 'neoforge'\n"
            + "    loaderVersion = '21.1.1'\n"
            + "    headless = true\n"
            + "    offline = true\n"
            + "    dummyAssets = true\n"
            + "    gameJvmArgs = ['-Xmx2G', '-Dkey=a value']\n"
            + "    gameArgs.add('--demo')\n"
            + "    joinServer = 'localhost'\n"
            + "    resolution = '800x600'\n"
            + "    retries = 2\n"
            + "    patchers = ['lwjgl']\n"
            + "    extraLaunchArgs.add('--some-new-flag')\n"
            + "}\n");
        build("runClient");
        Invocation invocation = invocations().get(0);
        assertEquals(List.of("launch", "--headless", "--offline",
            "--jvm", "\"-Xmx2G\" \"-Dkey=a value\"", "--game", "\"--demo\"",
            "--server", "localhost", "--resolution", "800x600", "--retries", "2", "--patchers", "lwjgl",
            "--some-new-flag", "neoforge", "1.21.1", "21.1.1"), invocation.args());
        assertEquals("true", invocation.property("hmc.assets.dummy"));
    }

    @Test
    void launchArgsOverride() {
        groovyBuild("tasks.register('runPack', HeadlessMcRunTask) {\n"
            + "    launchArgs = ['launch', '--packwiz', 'pack', 'client']\n"
            + "}\n");
        build("runPack");
        assertEquals(List.of("launch", "--packwiz", "pack", "client"), invocations().get(0).args());
    }

    @Test
    void modrinthAndCommandsRunBeforeLaunching() {
        groovyBuild("tasks.register('runClient', HeadlessMcRunTask) {\n"
            + "    minecraftVersion = '1.21.4'\n"
            + "    platform = 'fabric'\n"
            + "    modrinth 'fabric-api', 'mc-runtime-test'\n"
            + "    command 'java list'\n"
            + "    commands.add('config set \"hmc.some value\" x')\n"
            + "}\n");
        build("runClient");
        List<Invocation> invocations = invocations();
        assertEquals(5, invocations.size());
        assertEquals(List.of("mod", "add", "mod", "fabric-api", "fabric", "1.21.4"), invocations.get(0).args());
        assertEquals(List.of("mod", "add", "mod", "mc-runtime-test", "fabric", "1.21.4"), invocations.get(1).args());
        assertEquals(List.of("java", "list"), invocations.get(2).args());
        assertEquals(List.of("config", "set", "hmc.some value", "x"), invocations.get(3).args());
        assertEquals(List.of("launch", "fabric", "1.21.4"), invocations.get(4).args());
        // every invocation uses the same directories
        for (Invocation invocation : invocations) {
            assertEquals(invocations.get(4).property("hmc.files.game"), invocation.property("hmc.files.game"));
        }
    }

    @Test
    void modrinthWithLoaderVersion() {
        groovyBuild("tasks.register('runClient', HeadlessMcRunTask) {\n"
            + "    minecraftVersion = '1.21.1'\n"
            + "    platform = 'neoforge'\n"
            + "    loaderVersion = '21.1.265'\n"
            + "    modrinth 'mc-runtime-test'\n"
            + "}\n");
        build("runClient");
        List<Invocation> invocations = invocations();
        // HeadlessMc cannot resolve neoforge/1.21.1/21.1.265 before it is installed
        assertEquals(List.of("mod", "add", "mod", "mc-runtime-test", "neoforge", "1.21.1"), invocations.get(0).args());
        assertEquals(List.of("launch", "neoforge", "1.21.1", "21.1.265"), invocations.get(1).args());
    }

    @Test
    void minecraftOptionsAreMerged() throws Exception {
        write("run/headlessmc/game/runClient/options.txt", "lang:de_de\npauseOnLostFocus:true\n");
        groovyBuild("tasks.register('runClient', HeadlessMcRunTask) {\n"
            + "    minecraftVersion = '1.21.4'\n"
            + "    ciOptions()\n"
            + "    minecraftOptions.put('renderDistance', '2')\n"
            + "}\n");
        build("runClient");
        assertEquals(List.of("lang:de_de", "pauseOnLostFocus:false", "onboardAccessibility:false", "narrator:0",
            "renderDistance:2"), Files.readAllLines(file("run/headlessmc/game/runClient/options.txt")));
    }

    @Test
    void serverUsesTheHeadlessMcServerDirectory() {
        write("plugins/my-plugin.jar", "plugin");
        groovyBuild("tasks.register('runServer', HeadlessMcRunTask) {\n"
            + "    from('plugins')\n"
            + "    into(serverPluginsDirectory)\n"
            + "    server = true\n"
            + "    platform = HmcPlatform.PAPER\n"
            + "    minecraftVersion = '1.21.4'\n"
            + "    acceptEula = true\n"
            + "}\n");
        build("runServer");
        Invocation invocation = invocations().get(0);
        assertEquals(List.of("launch", "--eula-accept", "server", "paper", "1.21.4"), invocation.args());
        // HeadlessMc ignores hmc.files.game for servers, so it is not passed
        assertNull(invocation.property("hmc.files.game"));
        assertTrue(Files.isRegularFile(launcherDir().resolve("server/server-server-paper-1.21.4/plugins/my-plugin.jar")));
    }

    @Test
    void missingVersionFails() {
        groovyBuild("tasks.register('runClient', HeadlessMcRunTask)\n");
        BuildResult result = buildAndFail("runClient");
        assertTrue(result.getOutput().contains("minecraftVersion"), result.getOutput());
    }

    @Test
    void customGameDirectoryAndGameJava() {
        groovyBuild("tasks.register('runClient', HeadlessMcRunTask) {\n"
            + "    minecraftVersion = '1.21.4'\n"
            + "    gameDirectory = layout.projectDirectory.dir('my-game')\n"
            + "    gameJavaLaunchers.add(javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) })\n"
            + "}\n");
        build("runClient");
        Invocation invocation = invocations().get(0);
        assertEquals(projectDir.resolve("my-game").toAbsolutePath().toString(), invocation.property("hmc.files.game"));
        assertNotNull(invocation.property("hmc.java.versions"));
        assertTrue(Files.isDirectory(Path.of(invocation.property("hmc.java.versions"))));
    }

    @Test
    void kotlinDsl() {
        write("mods/a.jar", "a");
        kotlinBuild("tasks.register<io.github.headlesshq.headlessmc.gradle.HeadlessMcRunTask>(\"runClient\") {\n"
            + "    from(\"mods\")\n"
            + "    into(modsDirectory)\n"
            + "    minecraftVersion.set(\"1.21.4\")\n"
            + "    platform.set(io.github.headlesshq.headlessmc.gradle.HmcPlatform.FABRIC)\n"
            + "    headless.set(true)\n"
            + "    modrinth(\"fabric-api\")\n"
            + "}\n");
        build("runClient");
        List<Invocation> invocations = invocations();
        assertEquals(List.of("mod", "add", "mod", "fabric-api", "fabric", "1.21.4"), invocations.get(0).args());
        assertEquals(List.of("launch", "--headless", "fabric", "1.21.4"), invocations.get(1).args());
        assertTrue(Files.isRegularFile(file("run/headlessmc/game/runClient/mods/a.jar")));
    }

    @Test
    void configurationCache() {
        write("src/main/java/com/example/Mod.java", "package com.example; public class Mod {}");
        groovyBuild(JAVA_PROJECT
            + "tasks.register('runClient', HeadlessMcRunTask) {\n"
            + "    from(jar)\n"
            + "    into(modsDirectory)\n"
            + "    minecraftVersion = '1.21.4'\n"
            + "    modrinth 'fabric-api'\n"
            + "    ciOptions()\n"
            + "}\n"
            + "tasks.register('runServer', HeadlessMcRunTask) {\n"
            + "    from(jar)\n"
            + "    into(serverPluginsDirectory)\n"
            + "    server = true\n"
            + "    platform = 'paper'\n"
            + "    minecraftVersion = '1.21.4'\n"
            + "}\n");
        BuildResult first = build("runClient", "runServer", "--configuration-cache");
        assertTrue(first.getOutput().contains("Configuration cache entry stored"), first.getOutput());
        BuildResult second = build("runClient", "runServer", "--configuration-cache");
        assertTrue(second.getOutput().contains("Configuration cache entry reused"), second.getOutput());
        assertEquals(6, invocations().size());
        assertTrue(Files.isRegularFile(file("run/headlessmc/game/runClient/mods/mymod-1.0.0.jar")));
        assertTrue(Files.isRegularFile(launcherDir().resolve("server/server-server-paper-1.21.4/plugins/mymod-1.0.0.jar")));
    }

    @Test
    void multipleVersionsInALoop() {
        groovyBuild("['1.20.4', '1.21.1', '1.21.4'].each { mc ->\n"
            + "    tasks.register(\"runClient${mc.replace('.', '_')}\", HeadlessMcRunTask) {\n"
            + "        minecraftVersion = mc\n"
            + "        platform = 'fabric'\n"
            + "    }\n"
            + "}\n"
            + "tasks.register('runAll') { dependsOn tasks.withType(HeadlessMcRunTask) }\n");
        build("runAll", "--no-parallel");
        List<Invocation> invocations = invocations();
        assertEquals(3, invocations.size());
        assertEquals(List.of("launch", "fabric", "1.20.4"), invocations.get(0).args());
        assertEquals(List.of("launch", "fabric", "1.21.4"), invocations.get(2).args());
        assertTrue(invocations.get(0).property("hmc.files.game").endsWith("runClient1_20_4"));
    }

}
