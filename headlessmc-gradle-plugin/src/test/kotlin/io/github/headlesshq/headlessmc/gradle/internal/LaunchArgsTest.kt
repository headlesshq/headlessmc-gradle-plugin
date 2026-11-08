package io.github.headlesshq.headlessmc.gradle.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LaunchArgsTest {
    private fun simple(server: Boolean, platform: String, version: String, loader: String?) =
        LaunchArgs(server = server, platform = platform, minecraftVersion = version, loaderVersion = loader)

    @Test
    fun versionArgs() {
        assertEquals(listOf("1.21.4"), simple(false, "vanilla", "1.21.4", null).versionArgs())
        assertEquals(listOf("1.21.4"), simple(false, "VANILLA", "1.21.4", null).versionArgs())
        assertEquals(listOf("fabric", "1.21.4"), simple(false, "fabric", "1.21.4", null).versionArgs())
        assertEquals(listOf("fabric", "1.21.4", "0.16.14"), simple(false, "Fabric", "1.21.4", "0.16.14").versionArgs())
        assertEquals(listOf("server", "paper", "1.21.4"), simple(true, "paper", "1.21.4", null).versionArgs())
        assertEquals(listOf("server", "1.21.4"), simple(true, "vanilla", "1.21.4", null).versionArgs())
        assertEquals(listOf("server", "fabric", "1.21.4", "0.16.14"), simple(true, "fabric", "1.21.4", "0.16.14").versionArgs())
        // a build of vanilla needs the platform, otherwise it would be parsed as platform/version
        assertEquals(listOf("vanilla", "1.21.4", "x"), simple(false, "vanilla", "1.21.4", "x").versionArgs())
    }

    @Test
    fun modVersionArgsLeaveOutTheBuildForClients() {
        assertEquals(listOf("neoforge", "1.21.1"), simple(false, "neoforge", "1.21.1", "21.1.265").modVersionArgs())
        assertEquals(listOf("fabric", "1.21.4"), simple(false, "fabric", "1.21.4", null).modVersionArgs())
        assertEquals(listOf("1.21.4"), simple(false, "vanilla", "1.21.4", "x").modVersionArgs())
        assertEquals(listOf("server", "fabric", "1.21.4", "0.16.14"), simple(true, "fabric", "1.21.4", "0.16.14").modVersionArgs())
    }

    @Test
    fun minimalLaunch() {
        assertEquals(listOf("launch", "fabric", "1.21.4"), simple(false, "fabric", "1.21.4", null).toArgs())
    }

    @Test
    fun allOptions() {
        val args = LaunchArgs(
            server = false,
            platform = "neoforge",
            minecraftVersion = "1.21.1",
            loaderVersion = "21.1.1",
            headless = true,
            offline = true,
            acceptEula = true,
            jvmArgs = listOf("-Xmx2G", "-Dkey=a value"),
            gameArgs = listOf("--quickPlaySingleplayer", "New World"),
            joinServer = "localhost:25565",
            resolution = "800x600",
            retries = 3,
            patchers = listOf("lwjgl", "log4j"),
            extraArgs = listOf("--extra"),
        )
        assertEquals(
            listOf(
                "launch", "--headless", "--offline", "--eula-accept",
                "--jvm", "\"-Xmx2G\" \"-Dkey=a value\"",
                "--game", "\"--quickPlaySingleplayer\" \"New World\"",
                "--server", "localhost:25565",
                "--resolution", "800x600",
                "--retries", "3",
                "--patchers", "lwjgl,log4j",
                "--extra",
                "neoforge", "1.21.1", "21.1.1",
            ),
            args.toArgs(),
        )
    }

    @Test
    fun jvmArgsSurviveHeadlessMcSplitting() {
        val jvmArgs = listOf("-Dpath=C:\\Program Files\\x", "-Dquote=\"")
        val launch = LaunchArgs(minecraftVersion = "1.21", jvmArgs = jvmArgs).toArgs()
        assertEquals(jvmArgs, Args.split(launch[launch.indexOf("--jvm") + 1]))
    }

    @Test
    fun serverDirectoryNameMatchesHeadlessMc() {
        // GameDirService: "server-" + versionArg.toString("-"), the side is part of the version arg
        assertEquals("server-server-paper-1.21.4", simple(true, "paper", "1.21.4", null).serverDirectoryName())
        assertEquals("server-server-1.21.4", simple(true, "vanilla", "1.21.4", null).serverDirectoryName())
        assertEquals("server-server-1.21.4-x", simple(true, "vanilla", "1.21.4", "x").serverDirectoryName())
        assertEquals("server-server-fabric-1.21.4-0.16.14", simple(true, "fabric", "1.21.4", "0.16.14").serverDirectoryName())
    }
}
