package io.github.headlesshq.headlessmc.gradle.internal

/**
 * Quotes and splits command line arguments.
 *
 * HeadlessMc splits the values of options like `--jvm` with the JLine parser,
 * which understands double quotes and backslash escapes.
 * The same rules are used by [split], e.g. for `--args` on the command line.
 */
object Args {
    /**
     * @param arg the argument to quote.
     * @return the argument in double quotes, with backslashes and double quotes escaped.
     */
    @JvmStatic
    fun quote(arg: String): String = buildString(arg.length + 2) {
        append('"')
        for (c in arg) {
            if (c == '"' || c == '\\') {
                append('\\')
            }

            append(c)
        }

        append('"')
    }

    /**
     * @param args the arguments to join.
     * @return the arguments, each one [quoted][quote], separated by spaces.
     */
    @JvmStatic
    fun join(args: Collection<String>): String = args.joinToString(" ") { quote(it) }

    /**
     * Splits a command line into its arguments.
     * Arguments are separated by whitespace, double and single quotes group arguments,
     * a backslash escapes the next character (inside single quotes backslashes are kept).
     *
     * @param line the command line to split.
     * @return the arguments.
     * @throws IllegalArgumentException if a quote is not closed or the line ends with a backslash.
     */
    @JvmStatic
    fun split(line: String): List<String> {
        val result = ArrayList<String>()
        val current = StringBuilder()
        var inArg = false
        var quote: Char? = null
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quote == '\'' -> if (c == '\'') quote = null else current.append(c)
                c == '\\' -> {
                    require(i + 1 < line.length) { "Command line ends with an escape character: $line" }
                    current.append(line[++i])
                    inArg = true
                }
                quote == '"' -> if (c == '"') quote = null else current.append(c)
                c == '"' || c == '\'' -> {
                    quote = c
                    inArg = true
                }
                c.isWhitespace() -> if (inArg) {
                    result.add(current.toString())
                    current.setLength(0)
                    inArg = false
                }
                else -> {
                    current.append(c)
                    inArg = true
                }
            }

            i++
        }

        require(quote == null) { "Unclosed quote $quote in command line: $line" }
        if (inArg) {
            result.add(current.toString())
        }

        return result
    }

    /**
     * Quotes an argument for a Windows command line, so that the C runtime of the started process parses it back
     * into exactly this argument. Java only wraps arguments with spaces in quotes, without escaping quotes inside,
     * so e.g. the `--jvm` value of the launch command would be split differently.
     * Java passes arguments that start and end with a quote as they are.
     *
     * @param arg the argument.
     * @return the argument, quoted if necessary.
     */
    @JvmStatic
    fun quoteForWindows(arg: String): String {
        if (arg.isNotEmpty() && arg.none { it == ' ' || it == '\t' || it == '"' }) {
            return arg
        }

        return buildString(arg.length + 2) {
            append('"')
            var backslashes = 0
            for (c in arg) {
                when (c) {
                    '\\' -> backslashes++
                    '"' -> {
                        // backslashes before a quote are escaped, and the quote itself
                        repeat(backslashes * 2 + 1) { append('\\') }
                        backslashes = 0
                        append(c)
                    }
                    else -> {
                        repeat(backslashes) { append('\\') }
                        backslashes = 0
                        append(c)
                    }
                }
            }

            // backslashes before the closing quote are escaped
            repeat(backslashes * 2) { append('\\') }
            append('"')
        }
    }
}
