package io.github.headlesshq.headlessmc.gradle.internal

import java.nio.file.Files
import java.nio.file.Path

/**
 * Merges options into the Minecraft `options.txt`, which uses `key:value` lines.
 */
object MinecraftOptions {
    /**
     * @param optionsTxt the options.txt file, created if it does not exist.
     * @param options the options to set, existing values are replaced, other lines are kept.
     */
    @JvmStatic
    fun merge(optionsTxt: Path, options: Map<String, String>) {
        if (options.isEmpty()) {
            return
        }

        val lines = if (Files.exists(optionsTxt)) Files.readAllLines(optionsTxt, Charsets.UTF_8) else emptyList()
        val remaining = LinkedHashMap(options)
        val result = ArrayList<String>(lines.size + options.size)
        for (line in lines) {
            val key = line.substringBefore(':', missingDelimiterValue = "").takeIf { line.contains(':') }
            val value = key?.let { remaining.remove(it) }
            result.add(if (value != null) "$key:$value" else line)
        }

        remaining.forEach { (key, value) -> result.add("$key:$value") }
        Files.createDirectories(optionsTxt.toAbsolutePath().parent)
        Files.write(optionsTxt, result, Charsets.UTF_8)
    }
}
