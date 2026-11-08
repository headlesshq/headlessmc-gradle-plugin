package io.github.headlesshq.headlessmc.gradle.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MinecraftOptionsTest {
    @TempDir
    Path tempDir;

    @Test
    void createsTheFile() throws Exception {
        Path options = tempDir.resolve("game/options.txt");
        Map<String, String> values = new LinkedHashMap<>();
        values.put("pauseOnLostFocus", "false");
        values.put("narrator", "0");
        MinecraftOptions.merge(options, values);
        assertEquals(List.of("pauseOnLostFocus:false", "narrator:0"), Files.readAllLines(options));
    }

    @Test
    void replacesExistingValuesAndKeepsOtherLines() throws Exception {
        Path options = tempDir.resolve("options.txt");
        Files.write(options, List.of("version:3955", "pauseOnLostFocus:true", "no-colon", "lang:en_us"));
        MinecraftOptions.merge(options, Map.of("pauseOnLostFocus", "false", "fov", "0.5"));
        assertEquals(List.of("version:3955", "pauseOnLostFocus:false", "no-colon", "lang:en_us", "fov:0.5"),
            Files.readAllLines(options));
    }

    @Test
    void doesNothingWithoutOptions() throws Exception {
        Path options = tempDir.resolve("options.txt");
        MinecraftOptions.merge(options, Map.of());
        assertFalse(Files.exists(options));
    }

}
