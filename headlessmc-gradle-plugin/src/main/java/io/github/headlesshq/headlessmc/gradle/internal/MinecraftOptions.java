package io.github.headlesshq.headlessmc.gradle.internal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges options into the Minecraft {@code options.txt}, which uses {@code key:value} lines.
 */
public final class MinecraftOptions {
    private MinecraftOptions() {
        throw new AssertionError();
    }

    /**
     * @param optionsTxt the options.txt file, created if it does not exist.
     * @param options    the options to set, existing values are replaced, other lines are kept.
     * @throws IOException if the file cannot be read or written.
     */
    public static void merge(Path optionsTxt, Map<String, String> options) throws IOException {
        if (options.isEmpty()) {
            return;
        }

        List<String> lines = Files.exists(optionsTxt)
            ? Files.readAllLines(optionsTxt, StandardCharsets.UTF_8)
            : new ArrayList<>();
        Map<String, String> remaining = new LinkedHashMap<>(options);
        List<String> result = new ArrayList<>(lines.size() + options.size());
        for (String line : lines) {
            int index = line.indexOf(':');
            String key = index < 0 ? null : line.substring(0, index);
            if (key != null && remaining.containsKey(key)) {
                result.add(key + ":" + remaining.remove(key));
            } else {
                result.add(line);
            }
        }

        remaining.forEach((key, value) -> result.add(key + ":" + value));
        Files.createDirectories(optionsTxt.toAbsolutePath().getParent());
        Files.write(optionsTxt, result, StandardCharsets.UTF_8);
    }

}
