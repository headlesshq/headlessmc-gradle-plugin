package io.github.headlesshq.headlessmc.gradle.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Quotes and splits command line arguments.
 * <p>
 * HeadlessMc splits the values of options like {@code --jvm} with the JLine parser,
 * which understands double quotes and backslash escapes.
 * The same rules are used by {@link #split(String)}, e.g. for {@code --args} on the command line.
 */
public final class Args {
    private Args() {
        throw new AssertionError();
    }

    /**
     * @param arg the argument to quote.
     * @return the argument in double quotes, with backslashes and double quotes escaped.
     */
    public static String quote(String arg) {
        StringBuilder builder = new StringBuilder(arg.length() + 2).append('"');
        for (int i = 0; i < arg.length(); i++) {
            char c = arg.charAt(i);
            if (c == '"' || c == '\\') {
                builder.append('\\');
            }

            builder.append(c);
        }

        return builder.append('"').toString();
    }

    /**
     * @param args the arguments to join.
     * @return the arguments, each one {@link #quote(String) quoted}, separated by spaces.
     */
    public static String join(Collection<String> args) {
        return args.stream().map(Args::quote).collect(Collectors.joining(" "));
    }

    /**
     * Splits a command line into its arguments.
     * Arguments are separated by whitespace, double and single quotes group arguments,
     * a backslash escapes the next character (inside single quotes backslashes are kept).
     *
     * @param line the command line to split.
     * @return the arguments.
     * @throws IllegalArgumentException if a quote is not closed or the line ends with a backslash.
     */
    public static List<String> split(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inArg = false;
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quote == '\'') {
                if (c == '\'') {
                    quote = 0;
                } else {
                    current.append(c);
                }
            } else if (c == '\\') {
                if (i + 1 >= line.length()) {
                    throw new IllegalArgumentException("Command line ends with an escape character: " + line);
                }

                current.append(line.charAt(++i));
                inArg = true;
            } else if (quote == '"') {
                if (c == '"') {
                    quote = 0;
                } else {
                    current.append(c);
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
                inArg = true;
            } else if (Character.isWhitespace(c)) {
                if (inArg) {
                    result.add(current.toString());
                    current.setLength(0);
                    inArg = false;
                }
            } else {
                current.append(c);
                inArg = true;
            }
        }

        if (quote != 0) {
            throw new IllegalArgumentException("Unclosed quote " + quote + " in command line: " + line);
        }

        if (inArg) {
            result.add(current.toString());
        }

        return result;
    }

}
