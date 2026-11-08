package io.github.headlesshq.headlessmc.gradle.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * The version of this Gradle plugin and the default HeadlessMc version.
 */
public final class PluginInfo {
    private static final Properties PROPERTIES = load();

    private PluginInfo() {
        throw new AssertionError();
    }

    public static String getVersion() {
        return PROPERTIES.getProperty("version");
    }

    /**
     * @return the HeadlessMc version used by default.
     */
    public static String getHeadlessMcVersion() {
        return PROPERTIES.getProperty("headlessmc");
    }

    private static Properties load() {
        try (InputStream inputStream = PluginInfo.class.getResourceAsStream("/io/github/headlesshq/headlessmc/gradle/version.properties")) {
            if (inputStream == null) {
                throw new IllegalStateException("version.properties is missing");
            }

            Properties properties = new Properties();
            properties.load(inputStream);
            return properties;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
