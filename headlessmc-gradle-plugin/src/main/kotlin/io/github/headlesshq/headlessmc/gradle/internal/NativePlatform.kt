package io.github.headlesshq.headlessmc.gradle.internal

import java.util.Locale

/**
 * The operating systems and architectures HeadlessMc releases native executables for.
 *
 * @property assetName the name of the executable in the GitHub release.
 */
enum class NativePlatform(val assetName: String) {
    LINUX_X64("headlessmc-launcher-linux-x64"),
    LINUX_ARM64("headlessmc-launcher-linux-arm64"),
    MACOS_X64("headlessmc-launcher-macos-x64"),
    MACOS_ARM64("headlessmc-launcher-macos-arm64"),
    WINDOWS_X64("headlessmc-launcher-windows-x64.exe"),
    ;

    companion object {
        /**
         * @return the platform of the current machine, or `null` if HeadlessMc has no executable for it.
         */
        @JvmStatic
        fun current(): NativePlatform? = detect(System.getProperty("os.name", ""), System.getProperty("os.arch", ""))

        /**
         * @param osName the value of the os.name system property.
         * @param osArch the value of the os.arch system property.
         * @return the matching platform, or `null` if HeadlessMc has no executable for it.
         */
        @JvmStatic
        fun detect(osName: String, osArch: String): NativePlatform? {
            val os = osName.lowercase(Locale.ENGLISH)
            val arch = osArch.lowercase(Locale.ENGLISH)
            val x64 = arch == "amd64" || arch == "x86_64" || arch == "x64"
            val arm64 = arch == "aarch64" || arch == "arm64"
            return when {
                os.startsWith("windows") -> if (x64) WINDOWS_X64 else null
                os.startsWith("mac") || os.startsWith("darwin") -> if (x64) MACOS_X64 else if (arm64) MACOS_ARM64 else null
                os.startsWith("linux") -> if (x64) LINUX_X64 else if (arm64) LINUX_ARM64 else null
                else -> null
            }
        }
    }
}
