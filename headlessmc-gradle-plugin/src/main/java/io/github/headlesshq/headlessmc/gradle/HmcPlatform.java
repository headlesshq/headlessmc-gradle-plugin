package io.github.headlesshq.headlessmc.gradle;

/**
 * Names of the platforms (mod loaders and servers) HeadlessMc supports out of the box.
 * Platforms are plain strings, so that platforms added by HeadlessMc plugins, e.g. quilt, can be used, too.
 */
public final class HmcPlatform {
    public static final String VANILLA = "vanilla";
    public static final String FABRIC = "fabric";
    public static final String FORGE = "forge";
    public static final String NEOFORGE = "neoforge";
    /**
     * Server only.
     */
    public static final String PAPER = "paper";
    /**
     * Server only.
     */
    public static final String PURPUR = "purpur";

    private HmcPlatform() {
        throw new AssertionError();
    }

}
