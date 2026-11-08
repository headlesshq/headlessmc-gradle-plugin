package io.github.headlesshq.headlessmc.gradle.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class MinecraftOptionsTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun createsTheFile() {
        val options = tempDir.resolve("game/options.txt")
        MinecraftOptions.merge(options, linkedMapOf("pauseOnLostFocus" to "false", "narrator" to "0"))
        assertEquals(listOf("pauseOnLostFocus:false", "narrator:0"), Files.readAllLines(options))
    }

    @Test
    fun replacesExistingValuesAndKeepsOtherLines() {
        val options = tempDir.resolve("options.txt")
        Files.write(options, listOf("version:3955", "pauseOnLostFocus:true", "no-colon", "lang:en_us"))
        MinecraftOptions.merge(options, linkedMapOf("pauseOnLostFocus" to "false", "fov" to "0.5"))
        assertEquals(
            listOf("version:3955", "pauseOnLostFocus:false", "no-colon", "lang:en_us", "fov:0.5"),
            Files.readAllLines(options),
        )
    }

    @Test
    fun doesNothingWithoutOptions() {
        val options = tempDir.resolve("options.txt")
        MinecraftOptions.merge(options, emptyMap())
        assertFalse(Files.exists(options))
    }
}
