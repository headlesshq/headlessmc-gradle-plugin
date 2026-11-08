package io.github.headlesshq.headlessmc.gradle.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class NativePlatformTest {
    @ParameterizedTest
    @CsvSource(
        "Linux, amd64, LINUX_X64",
        "Linux, x86_64, LINUX_X64",
        "Linux, aarch64, LINUX_ARM64",
        "Mac OS X, aarch64, MACOS_ARM64",
        "Mac OS X, x86_64, MACOS_X64",
        "Windows 11, amd64, WINDOWS_X64",
        "Windows Server 2022, amd64, WINDOWS_X64",
    )
    fun detectsSupportedPlatforms(os: String, arch: String, expected: NativePlatform) {
        assertEquals(expected, NativePlatform.detect(os, arch))
    }

    @ParameterizedTest
    @CsvSource("Linux, x86", "Linux, riscv64", "Windows 11, aarch64", "FreeBSD, amd64", "SunOS, sparcv9")
    fun unsupportedPlatforms(os: String, arch: String) {
        assertNull(NativePlatform.detect(os, arch))
    }

    @ParameterizedTest
    @CsvSource(
        "LINUX_X64, headlessmc-launcher-linux-x64",
        "WINDOWS_X64, headlessmc-launcher-windows-x64.exe",
        "MACOS_ARM64, headlessmc-launcher-macos-arm64",
    )
    fun assetNamesMatchTheRelease(platform: NativePlatform, name: String) {
        assertEquals(name, platform.assetName)
    }
}
