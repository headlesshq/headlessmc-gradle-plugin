package io.github.headlesshq.headlessmc.gradle.internal

import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import java.io.IOException
import java.io.UncheckedIOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat
import java.util.Locale

/**
 * Downloads the native HeadlessMc executable from a GitHub release and verifies its SHA-256 hash.
 */
class NativeDownloader(private val logger: Logger) {
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    /**
     * Makes sure the executable exists, downloading it if necessary.
     *
     * @param executable where the executable is placed.
     * @param baseUrl the url the release assets are found under, the version and asset name are appended.
     * @param version the HeadlessMc version.
     * @param platform the platform to download the executable for.
     * @param expectedSha256 the expected hash, if `null` the hash is taken from the GitHub API.
     * @param offline if Gradle runs in offline mode.
     */
    fun ensure(
        executable: Path,
        baseUrl: String,
        version: String,
        platform: NativePlatform,
        expectedSha256: String?,
        offline: Boolean,
    ) {
        if (expectedSha256 != null && !SHA256.matches(expectedSha256)) {
            throw GradleException("nativeSha256 is not a SHA-256 hash: $expectedSha256")
        }

        if (Files.isRegularFile(executable)) {
            if (expectedSha256 == null || expectedSha256.equals(sha256(executable), ignoreCase = true)) {
                makeExecutable(executable)
                return
            }

            logger.warn("[HeadlessMc] {} does not match nativeSha256, downloading it again.", executable)
        }

        if (offline) {
            throw GradleException(
                "The HeadlessMc executable $executable has not been downloaded yet, but Gradle runs in offline mode.",
            )
        }

        val url = baseUrl.removeSuffix("/") + "/" + version + "/" + platform.assetName
        val expected = expectedSha256 ?: gitHubDigest(baseUrl, version, platform)
        try {
            Files.createDirectories(executable.toAbsolutePath().parent)
            val part = executable.resolveSibling("${executable.fileName}.part")
            logger.lifecycle("[HeadlessMc] Downloading {}", url)
            val response = client.send(
                HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(10)).GET().build(),
                HttpResponse.BodyHandlers.ofFile(part),
            )
            if (response.statusCode() != 200) {
                Files.deleteIfExists(part)
                throw GradleException("Failed to download $url: HTTP ${response.statusCode()}")
            }

            val actual = sha256(part)
            if (expected != null && !expected.equals(actual, ignoreCase = true)) {
                Files.deleteIfExists(part)
                throw GradleException("The SHA-256 hash of $url is $actual, expected $expected")
            }

            Files.move(part, executable, StandardCopyOption.REPLACE_EXISTING)
            makeExecutable(executable)
        } catch (e: IOException) {
            throw GradleException("Failed to download $url", e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw GradleException("Interrupted while downloading $url", e)
        }
    }

    private fun gitHubDigest(baseUrl: String, version: String, platform: NativePlatform): String? {
        if (baseUrl.removeSuffix("/") != GITHUB_RELEASES) {
            logger.warn("[HeadlessMc] Cannot verify the executable downloaded from {}, set nativeSha256.", baseUrl)
            return null
        }

        val url = GITHUB_API_RELEASES + version
        try {
            val response = client.send(
                HttpRequest.newBuilder(URI.create(url))
                    .header("Accept", "application/vnd.github+json")
                    .timeout(Duration.ofMinutes(1))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString(Charsets.UTF_8),
            )
            if (response.statusCode() == 200) {
                findDigest(response.body(), platform.assetName)?.let { return it }
            }

            logger.warn(
                "[HeadlessMc] Could not get the SHA-256 hash of {} from {} (HTTP {}), the download cannot be verified, set nativeSha256.",
                platform.assetName, url, response.statusCode(),
            )
        } catch (e: IOException) {
            logger.warn(
                "[HeadlessMc] Could not get the SHA-256 hash of {} from {}: {}, the download cannot be verified, set nativeSha256.",
                platform.assetName, url, e.toString(),
            )
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw GradleException("Interrupted while requesting $url", e)
        }

        return null
    }

    companion object {
        const val GITHUB_RELEASES: String = "https://github.com/headlesshq/headlessmc/releases/download"
        const val GITHUB_API_RELEASES: String = "https://api.github.com/repos/headlesshq/headlessmc/releases/tags/"
        private val DIGEST = Regex("\"digest\"\\s*:\\s*\"sha256:([0-9a-fA-F]{64})\"")
        private val SHA256 = Regex("[0-9a-fA-F]{64}")

        /**
         * @param releaseJson the json of a release from the GitHub API.
         * @param assetName the name of the asset.
         * @return the SHA-256 digest of the asset, or `null` if the json does not contain it.
         */
        @JvmStatic
        fun findDigest(releaseJson: String, assetName: String): String? {
            val name = Regex("\"name\"\\s*:\\s*\"" + Regex.escape(assetName) + "\"").find(releaseJson) ?: return null
            val start = name.range.last + 1
            // browser_download_url is the last field of every asset
            val end = releaseJson.indexOf("\"browser_download_url\"", start).takeIf { it >= 0 } ?: releaseJson.length
            return DIGEST.find(releaseJson.substring(start, end))?.groupValues?.get(1)?.lowercase(Locale.ENGLISH)
        }

        /**
         * @param file the file to hash.
         * @return the hex encoded SHA-256 hash of the file.
         */
        @JvmStatic
        fun sha256(file: Path): String {
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                Files.newInputStream(file).use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        digest.update(buffer, 0, read)
                    }
                }

                return HexFormat.of().formatHex(digest.digest())
            } catch (e: IOException) {
                throw UncheckedIOException("Failed to hash $file", e)
            }
        }

        private fun makeExecutable(executable: Path) {
            if (!Files.isExecutable(executable) && !executable.toFile().setExecutable(true)) {
                throw GradleException("Failed to make $executable executable")
            }
        }
    }
}
