package io.github.headlesshq.headlessmc.gradle.internal;

import org.gradle.api.GradleException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Makes sure that HeadlessMc only places its files inside the Gradle build.
 * HeadlessMc would otherwise use the users home directory, e.g. ~/.minecraft, ~/.config/headlessmc.
 */
public final class ProjectScope {
    /**
     * Configuration keys that would move HeadlessMc files somewhere else.
     * They are managed by the Gradle plugin.
     */
    public static final Set<String> MANAGED_KEYS = Set.of(
        "hmc.files.location",
        "hmc.files.mc",
        "hmc.files.game",
        // legacy names of hmc.files.mc and hmc.files.game
        "hmc.mcdir",
        "hmc.gamedir"
    );

    private final Path root;

    /**
     * @param root the directory everything has to be placed in, the root directory of the build.
     */
    public ProjectScope(File root) {
        this.root = normalize(root.toPath());
    }

    public Path getRoot() {
        return root;
    }

    /**
     * @param name a name for the directory, used in the error message.
     * @param directory the directory to check.
     * @return the normalized absolute directory.
     * @throws GradleException if the directory is not inside the {@link #getRoot() root}.
     */
    public Path check(String name, File directory) {
        Path path = normalize(directory.toPath());
        if (!path.startsWith(root) || path.equals(root)) {
            throw new GradleException("HeadlessMc may only use directories inside the build at " + root
                + ", but " + name + " is " + path + ". Choose a directory inside the project.");
        }

        return path;
    }

    /**
     * @param source where the configuration comes from, for the error message.
     * @param config HeadlessMc configuration given by the user.
     * @throws GradleException if the configuration contains a {@link #MANAGED_KEYS managed key}.
     */
    public static void checkConfig(String source, Map<String, String> config) {
        for (String key : config.keySet()) {
            checkKey(source, key);
        }
    }

    /**
     * @param source where the arguments come from, for the error message.
     * @param jvmArgs JVM arguments given by the user.
     * @throws GradleException if the arguments set a {@link #MANAGED_KEYS managed key} as system property.
     */
    public static void checkJvmArgs(String source, Iterable<String> jvmArgs) {
        for (String arg : jvmArgs) {
            if (arg.startsWith("-D")) {
                int index = arg.indexOf('=');
                checkKey(source, index < 0 ? arg.substring(2) : arg.substring(2, index));
            }
        }
    }

    private static void checkKey(String source, String key) {
        if (MANAGED_KEYS.contains(key.trim().toLowerCase(Locale.ENGLISH))) {
            throw new GradleException(source + " sets " + key + ", which is managed by the HeadlessMc Gradle plugin,"
                + " so that HeadlessMc only places files inside the project."
                + " Use the directory properties of the headlessmc extension or the task instead.");
        }
    }

    /**
     * Absolute, normalized and with symlinks of the existing part of the path resolved,
     * so that a symlink inside the project cannot point outside of it.
     */
    static Path normalize(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        Path existing = absolute;
        while (existing != null && !Files.exists(existing)) {
            existing = existing.getParent();
        }

        if (existing == null) {
            return absolute;
        }

        try {
            return existing.toRealPath().resolve(existing.relativize(absolute)).normalize();
        } catch (IOException e) {
            return absolute;
        }
    }

}
