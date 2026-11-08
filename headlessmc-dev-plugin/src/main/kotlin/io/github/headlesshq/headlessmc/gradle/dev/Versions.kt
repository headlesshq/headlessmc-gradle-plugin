package io.github.headlesshq.headlessmc.gradle.dev

/**
 * A simple comparison of dot or dash separated versions, numeric parts are compared as numbers.
 */
internal object Versions {
    private val SEPARATOR = Regex("[.\\-]")

    @JvmStatic
    fun major(version: String): String = version.split(SEPARATOR)[0]

    @JvmStatic
    fun compare(first: String, second: String): Int {
        val a = first.split(SEPARATOR)
        val b = second.split(SEPARATOR)
        for (i in 0 until minOf(a.size, b.size)) {
            val result = if (isNumber(a[i]) && isNumber(b[i])) a[i].toLong().compareTo(b[i].toLong()) else a[i].compareTo(b[i])
            if (result != 0) {
                return result
            }
        }

        return a.size.compareTo(b.size)
    }

    private fun isNumber(part: String): Boolean = part.isNotEmpty() && part.length < 19 && part.all(Char::isDigit)
}
