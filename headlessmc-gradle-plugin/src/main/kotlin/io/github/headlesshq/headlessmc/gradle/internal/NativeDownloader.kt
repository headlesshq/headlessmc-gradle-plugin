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
 * An executable is never run unverified: the hash is either given, pinned in the plugin for the
 * release it was built for, or taken from the GitHub API. If none is available the download fails.
 */
class NativeDownloader @JvmOverloads constructor(
    private val logger: Logger,
    private val retryDelay: Duration = Duration.ofSeconds(2),
    private val gitHubToken: String? = System.getenv("GITHUB_TOKEN"),
) {
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
     * @param expectedSha256 the expected hash, if `null` the pinned hash or the hash from the GitHub API is used.
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

        val known = expectedSha256 ?: pinnedDigest(baseUrl, version, platform)
        val sidecar = executable.resolveSibling("${executable.fileName}.sha256")
        if (Files.isRegularFile(executable)) {
            // without a known hash, only trust a file if it still matches the hash it was verified with
            val trusted = known ?: readSidecar(sidecar)
            if (trusted != null && trusted.equals(sha256(executable), ignoreCase = true)) {
                makeExecutable(executable)
                return
            }

            logger.warn("[HeadlessMc] {} does not match the expected SHA-256 hash, downloading it again.", executable)
        }

        if (offline) {
            throw GradleException(
                "The HeadlessMc executable $executable has not been downloaded yet, but Gradle runs in offline mode.",
            )
        }

        val url = baseUrl.removeSuffix("/") + "/" + version + "/" + platform.assetName
        val expected = known ?: gitHubDigest(baseUrl, version, platform)
            ?: throw GradleException(
                "Cannot verify the HeadlessMc executable $url: no SHA-256 hash is known for it. " +
                    "Set nativeSha256 to the hash of the executable, or use the default nativeDownloadUrl " +
                    "(optionally with a GITHUB_TOKEN in the environment if the GitHub API is rate limited).",
            )
        try {
            Files.createDirectories(executable.toAbsolutePath().parent)
            val part = executable.resolveSibling("${executable.fileName}.part")
            logger.lifecycle("[HeadlessMc] Downloading {}", url)
            val response = send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(10)).GET().build()) {
                HttpResponse.BodyHandlers.ofFile(part)
            }
            if (response.statusCode() != 200) {
                Files.deleteIfExists(part)
                throw GradleException("Failed to download $url: HTTP ${response.statusCode()}")
            }

            val actual = sha256(part)
            if (!expected.equals(actual, ignoreCase = true)) {
                Files.deleteIfExists(part)
                throw GradleException("The SHA-256 hash of $url is $actual, expected $expected")
            }

            Files.move(part, executable, StandardCopyOption.REPLACE_EXISTING)
            Files.writeString(sidecar, actual.lowercase(Locale.ENGLISH))
            makeExecutable(executable)
        } catch (e: IOException) {
            throw GradleException("Failed to download $url", e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw GradleException("Interrupted while downloading $url", e)
        }
    }

    /**
     * Sends the request, retrying on network errors and server errors (5xx, 429), which GitHub returns now and then.
     */
    private fun <T> send(request: HttpRequest, bodyHandler: () -> HttpResponse.BodyHandler<T>): HttpResponse<T> {
        var attempt = 1
        while (true) {
            val response = try {
                client.send(request, bodyHandler())
            } catch (e: IOException) {
                if (attempt >= ATTEMPTS) {
                    throw e
                }

                logger.warn("[HeadlessMc] Request to {} failed: {}, retrying.", request.uri(), e.toString())
                null
            }

            val status = response?.statusCode()
            if (response != null && (attempt >= ATTEMPTS || (status != 429 && status!! < 500))) {
                return response
            }

            if (response != null) {
                logger.warn("[HeadlessMc] Request to {} failed with HTTP {}, retrying.", request.uri(), status)
            }

            Thread.sleep(retryDelay.toMillis() * (1L shl (attempt - 1)))
            attempt++
        }
    }

    private fun pinnedDigest(baseUrl: String, version: String, platform: NativePlatform): String? =
        if (baseUrl.removeSuffix("/") == GITHUB_RELEASES) PINNED[version]?.get(platform.assetName) else null

    private fun readSidecar(sidecar: Path): String? {
        return try {
            Files.readString(sidecar).trim().takeIf { SHA256.matches(it) }
        } catch (e: IOException) {
            null
        }
    }

    private fun gitHubDigest(baseUrl: String, version: String, platform: NativePlatform): String? {
        if (baseUrl.removeSuffix("/") != GITHUB_RELEASES) {
            return null
        }

        val url = GITHUB_API_RELEASES + version
        try {
            val builder = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/vnd.github+json")
                .timeout(Duration.ofMinutes(1))
            if (!gitHubToken.isNullOrBlank()) {
                builder.header("Authorization", "Bearer $gitHubToken")
            }

            val request = builder.GET().build()
            val response = send(request) { HttpResponse.BodyHandlers.ofString(Charsets.UTF_8) }
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
        private const val ATTEMPTS = 4

        /**
         * SHA-256 hashes of the executables of the HeadlessMc releases this plugin was built for.
         */
        private val PINNED: Map<String, Map<String, String>> = mapOf(
            "3.0.0-RC8" to mapOf(
                "headlessmc-launcher-linux-arm64" to "0d5298df7f344b529cb63c1203e8e25ccb0f3bb58d6795e38ad8f3cf6c331dec",
                "headlessmc-launcher-linux-x64" to "62bbcd85344470bc1c11e08be4eda33ce06aeff2ed2931c87964640d5a733ece",
                "headlessmc-launcher-macos-arm64" to "fc9d636a715a206e11480869c2ef7e97a9ba919e84d56b376aa301360d105060",
                "headlessmc-launcher-macos-x64" to "d37e9d206b0245f13f9855c16c3ebb1554542fa694b10bcaacf2afe5b928fb67",
                "headlessmc-launcher-windows-x64.exe" to "72c42ddd7240f08c79615bbf5e9bef15d209d3e75017f4ef9d9dde9f5028f935",
            ),
        )

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
