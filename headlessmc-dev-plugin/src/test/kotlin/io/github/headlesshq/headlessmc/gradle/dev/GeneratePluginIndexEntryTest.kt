package io.github.headlesshq.headlessmc.gradle.dev

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class GeneratePluginIndexEntryTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun defaultDownloadUrlOfGitHubHomepages() {
        val expected = "https://github.com/owner/repo/releases/download/1.0.0/plugin-1.0.0.jar"
        listOf(
            "https://github.com/owner/repo",
            "https://github.com/owner/repo/",
            "https://github.com/owner/repo.git",
            "http://www.github.com/owner/repo",
            "https://github.com/owner/repo/tree/main/examples/plugin",
            "https://github.com/owner/repo#readme",
        ).forEach { assertEquals(expected, GeneratePluginIndexEntry.defaultDownloadUrl(it, "1.0.0", "plugin-1.0.0.jar"), it) }
    }

    @Test
    fun noDefaultDownloadUrlOfOtherHomepages() {
        listOf(null, "", "https://example.com/owner/repo", "https://github.com/owner", "https://gitlab.com/owner/repo")
            .forEach { assertNull(GeneratePluginIndexEntry.defaultDownloadUrl(it, "1.0.0", "plugin-1.0.0.jar"), it) }
    }

    @Test
    fun writesEntryWithHashAndSizeOfTheJar() {
        val jar = dir.resolve("plugin-1.0.0.jar")
        Files.write(jar, "jar".toByteArray())
        val release = GeneratePluginIndexEntry.release("1.0.0", "[3.0.0-RC9,4)", 25, "https://example.com/plugin.jar", jar)
        val entry = GeneratePluginIndexEntry.entry("example", "Example", null, listOf("someone"), "MIT", null, null, release)
        assertEquals(
            """
            {
              "id": "example",
              "name": "Example",
              "authors": [
                "someone"
              ],
              "license": "MIT",
              "downloads": [
                {
                  "version": "1.0.0",
                  "headlessmc": "[3.0.0-RC9,4)",
                  "java": 25,
                  "url": "https://example.com/plugin.jar",
                  "sha256": "0163f1eea7894350060624d315234d40c508ab251ba121714e234503045faadd",
                  "size": 3
                }
              ]
            }

            """.trimIndent(),
            Json.write(entry),
        )
    }
}
