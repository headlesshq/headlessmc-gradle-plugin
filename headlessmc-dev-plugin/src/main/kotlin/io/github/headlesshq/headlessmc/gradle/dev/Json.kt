package io.github.headlesshq.headlessmc.gradle.dev

/**
 * Writes maps, lists, strings, numbers and booleans as pretty-printed JSON, so the plugin needs no dependencies.
 */
internal object Json {
    private const val INDENT = "  "

    @JvmStatic
    fun write(value: Any): String = buildString {
        write(this, value, "")
        append('\n')
    }

    private fun write(builder: StringBuilder, value: Any?, indent: String) {
        when (value) {
            is Map<*, *> -> container(builder, value.entries, '{', '}', indent) { entry ->
                string(builder, entry.key.toString())
                builder.append(": ")
                write(builder, entry.value, indent + INDENT)
            }
            is Collection<*> -> container(builder, value, '[', ']', indent) { write(builder, it, indent + INDENT) }
            is Number, is Boolean -> builder.append(value)
            else -> string(builder, value.toString())
        }
    }

    private fun <T> container(builder: StringBuilder, elements: Collection<T>, open: Char, close: Char, indent: String, element: (T) -> Unit) {
        builder.append(open)
        if (elements.isEmpty()) {
            builder.append(close)
            return
        }

        builder.append('\n')
        elements.forEachIndexed { i, value ->
            builder.append(indent).append(INDENT)
            element(value)
            builder.append(if (i + 1 < elements.size) ",\n" else "\n")
        }

        builder.append(indent).append(close)
    }

    private fun string(builder: StringBuilder, string: String) {
        builder.append('"')
        for (c in string) {
            when {
                c == '"' -> builder.append("\\\"")
                c == '\\' -> builder.append("\\\\")
                c == '\n' -> builder.append("\\n")
                c == '\r' -> builder.append("\\r")
                c == '\t' -> builder.append("\\t")
                c < ' ' -> builder.append(String.format("\\u%04x", c.code))
                else -> builder.append(c)
            }
        }

        builder.append('"')
    }
}
