package io.github.headlesshq.headlessmc.gradle.internal;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * The operating systems and architectures HeadlessMc releases native executables for.
 */
public enum NativePlatform {
    LINUX_X64("headlessmc-launcher-linux-x64"),
    LINUX_ARM64("headlessmc-launcher-linux-arm64"),
    MACOS_X64("headlessmc-launcher-macos-x64"),
    MACOS_ARM64("headlessmc-launcher-macos-arm64"),
    WINDOWS_X64("headlessmc-launcher-windows-x64.exe");

    private final String assetName;

    NativePlatform(String assetName) {
        this.assetName = assetName;
    }

    /**
     * @return the name of the executable in the GitHub release.
     */
    public String getAssetName() {
        return assetName;
    }

    /**
     * @return the platform of the current machine, or {@code null} if HeadlessMc has no executable for it.
     */
    public static @Nullable NativePlatform current() {
        return detect(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
    }

    /**
     * @param osName the value of the os.name system property.
     * @param osArch the value of the os.arch system property.
     * @return the matching platform, or {@code null} if HeadlessMc has no executable for it.
     */
    public static @Nullable NativePlatform detect(String osName, String osArch) {
        String os = osName.toLowerCase(Locale.ENGLISH);
        String arch = osArch.toLowerCase(Locale.ENGLISH);
        boolean x64 = arch.equals("amd64") || arch.equals("x86_64") || arch.equals("x64");
        boolean arm64 = arch.equals("aarch64") || arch.equals("arm64");
        if (os.startsWith("windows")) {
            return x64 ? WINDOWS_X64 : null;
        } else if (os.startsWith("mac") || os.startsWith("darwin")) {
            return x64 ? MACOS_X64 : arm64 ? MACOS_ARM64 : null;
        } else if (os.startsWith("linux")) {
            return x64 ? LINUX_X64 : arm64 ? LINUX_ARM64 : null;
        }

        return null;
    }

}
