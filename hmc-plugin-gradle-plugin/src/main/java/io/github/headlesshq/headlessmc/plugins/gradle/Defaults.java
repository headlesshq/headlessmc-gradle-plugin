package io.github.headlesshq.headlessmc.plugins.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * The HeadlessMc plugins are built against by default.
 * Until HeadlessMc 3.0.0-RC9 is released, this is a snapshot, e.g. a local build or an included build.
 */
final class Defaults {
    private static final Properties PROPERTIES = load();

    /**
     * The Maven group of the default HeadlessMc.
     */
    static final String GROUP = PROPERTIES.getProperty("group");
    /**
     * The version of the default HeadlessMc.
     */
    static final String VERSION = PROPERTIES.getProperty("version");
    /**
     * The HeadlessMc versions plugins built against the default HeadlessMc are compatible with.
     */
    static final String COMPATIBLE = PROPERTIES.getProperty("compatible");

    private Defaults() {
        throw new AssertionError();
    }

    private static Properties load() {
        try (InputStream inputStream = Defaults.class.getResourceAsStream("headlessmc.properties")) {
            if (inputStream == null) {
                throw new IllegalStateException("headlessmc.properties is missing");
            }

            Properties properties = new Properties();
            properties.load(inputStream);
            return properties;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
