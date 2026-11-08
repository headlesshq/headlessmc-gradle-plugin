package io.github.headlesshq.headlessmc.gradle.internal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LaunchArgsTest {
    private static LaunchArgs simple(boolean server, String platform, String version, String loader) {
        return new LaunchArgs(server, platform, version, loader, false, false, false,
            List.of(), List.of(), null, null, null, List.of(), List.of());
    }

    @Test
    void versionArgs() {
        assertEquals(List.of("1.21.4"), simple(false, "vanilla", "1.21.4", null).versionArgs());
        assertEquals(List.of("1.21.4"), simple(false, "VANILLA", "1.21.4", null).versionArgs());
        assertEquals(List.of("fabric", "1.21.4"), simple(false, "fabric", "1.21.4", null).versionArgs());
        assertEquals(List.of("fabric", "1.21.4", "0.16.14"), simple(false, "Fabric", "1.21.4", "0.16.14").versionArgs());
        assertEquals(List.of("server", "paper", "1.21.4"), simple(true, "paper", "1.21.4", null).versionArgs());
        assertEquals(List.of("server", "1.21.4"), simple(true, "vanilla", "1.21.4", null).versionArgs());
        assertEquals(List.of("server", "fabric", "1.21.4", "0.16.14"), simple(true, "fabric", "1.21.4", "0.16.14").versionArgs());
        // a build of vanilla needs the platform, otherwise it would be parsed as platform/version
        assertEquals(List.of("vanilla", "1.21.4", "x"), simple(false, "vanilla", "1.21.4", "x").versionArgs());
    }

    @Test
    void modVersionArgsLeaveOutTheBuildForClients() {
        assertEquals(List.of("neoforge", "1.21.1"), simple(false, "neoforge", "1.21.1", "21.1.265").modVersionArgs());
        assertEquals(List.of("fabric", "1.21.4"), simple(false, "fabric", "1.21.4", null).modVersionArgs());
        assertEquals(List.of("1.21.4"), simple(false, "vanilla", "1.21.4", "x").modVersionArgs());
        assertEquals(List.of("server", "fabric", "1.21.4", "0.16.14"), simple(true, "fabric", "1.21.4", "0.16.14").modVersionArgs());
    }

    @Test
    void minimalLaunch() {
        assertEquals(List.of("launch", "fabric", "1.21.4"), simple(false, "fabric", "1.21.4", null).toArgs());
    }

    @Test
    void allOptions() {
        LaunchArgs args = new LaunchArgs(false, "neoforge", "1.21.1", "21.1.1", true, true, true,
            List.of("-Xmx2G", "-Dkey=a value"), List.of("--quickPlaySingleplayer", "New World"),
            "localhost:25565", "800x600", 3, List.of("lwjgl", "log4j"), List.of("--extra"));
        assertEquals(List.of(
            "launch", "--headless", "--offline", "--eula-accept",
            "--jvm", "\"-Xmx2G\" \"-Dkey=a value\"",
            "--game", "\"--quickPlaySingleplayer\" \"New World\"",
            "--server", "localhost:25565",
            "--resolution", "800x600",
            "--retries", "3",
            "--patchers", "lwjgl,log4j",
            "--extra",
            "neoforge", "1.21.1", "21.1.1"
        ), args.toArgs());
    }

    @Test
    void jvmArgsSurviveHeadlessMcSplitting() {
        List<String> jvmArgs = List.of("-Dpath=C:\\Program Files\\x", "-Dquote=\"");
        LaunchArgs args = new LaunchArgs(false, "vanilla", "1.21", null, false, false, false,
            jvmArgs, List.of(), null, null, null, List.of(), List.of());
        List<String> launch = args.toArgs();
        assertEquals(jvmArgs, Args.split(launch.get(launch.indexOf("--jvm") + 1)));
    }

    @Test
    void serverDirectoryNameMatchesHeadlessMc() {
        // GameDirService: "server-" + versionArg.toString("-"), the side is part of the version arg
        assertEquals("server-server-paper-1.21.4", simple(true, "paper", "1.21.4", null).serverDirectoryName());
        assertEquals("server-server-1.21.4", simple(true, "vanilla", "1.21.4", null).serverDirectoryName());
        assertEquals("server-server-1.21.4-x", simple(true, "vanilla", "1.21.4", "x").serverDirectoryName());
        assertEquals("server-server-fabric-1.21.4-0.16.14", simple(true, "fabric", "1.21.4", "0.16.14").serverDirectoryName());
    }

}
