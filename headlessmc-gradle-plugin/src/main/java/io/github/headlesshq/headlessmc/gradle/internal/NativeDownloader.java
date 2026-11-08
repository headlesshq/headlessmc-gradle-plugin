package io.github.headlesshq.headlessmc.gradle.internal;

import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Downloads the native HeadlessMc executable from a GitHub release and verifies its SHA-256 hash.
 */
public final class NativeDownloader {
    public static final String GITHUB_RELEASES = "https://github.com/headlesshq/headlessmc/releases/download";
    public static final String GITHUB_API_RELEASES = "https://api.github.com/repos/headlesshq/headlessmc/releases/tags/";
    private static final Pattern DIGEST = Pattern.compile("\"digest\"\\s*:\\s*\"sha256:([0-9a-fA-F]{64})\"");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");

    private final Logger logger;
    private final HttpClient client;

    public NativeDownloader(Logger logger) {
        this.logger = logger;
        this.client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    /**
     * Makes sure the executable exists, downloading it if necessary.
     *
     * @param executable where the executable is placed.
     * @param baseUrl the url the release assets are found under, the version and asset name are appended.
     * @param version the HeadlessMc version.
     * @param platform the platform to download the executable for.
     * @param expectedSha256 the expected hash, if {@code null} the hash is taken from the GitHub API.
     * @param offline if Gradle runs in offline mode.
     */
    public void ensure(Path executable, String baseUrl, String version, NativePlatform platform,
                       @Nullable String expectedSha256, boolean offline) {
        if (expectedSha256 != null && !SHA256.matcher(expectedSha256).matches()) {
            throw new GradleException("nativeSha256 is not a SHA-256 hash: " + expectedSha256);
        }

        if (Files.isRegularFile(executable)) {
            if (expectedSha256 == null || expectedSha256.equalsIgnoreCase(sha256(executable))) {
                makeExecutable(executable);
                return;
            }

            logger.warn("[HeadlessMc] {} does not match nativeSha256, downloading it again.", executable);
        }

        if (offline) {
            throw new GradleException("The HeadlessMc executable " + executable + " has not been downloaded yet,"
                + " but Gradle runs in offline mode.");
        }

        String url = stripTrailingSlash(baseUrl) + "/" + version + "/" + platform.getAssetName();
        String expected = expectedSha256;
        if (expected == null) {
            expected = gitHubDigest(baseUrl, version, platform);
        }

        try {
            Files.createDirectories(executable.toAbsolutePath().getParent());
            Path part = executable.resolveSibling(executable.getFileName() + ".part");
            logger.lifecycle("[HeadlessMc] Downloading {}", url);
            HttpResponse<Path> response = client.send(
                HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(10)).GET().build(),
                HttpResponse.BodyHandlers.ofFile(part));
            if (response.statusCode() != 200) {
                Files.deleteIfExists(part);
                throw new GradleException("Failed to download " + url + ": HTTP " + response.statusCode());
            }

            String actual = sha256(part);
            if (expected != null && !expected.equalsIgnoreCase(actual)) {
                Files.deleteIfExists(part);
                throw new GradleException("The SHA-256 hash of " + url + " is " + actual + ", expected " + expected);
            }

            Files.move(part, executable, StandardCopyOption.REPLACE_EXISTING);
            makeExecutable(executable);
        } catch (IOException e) {
            throw new GradleException("Failed to download " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GradleException("Interrupted while downloading " + url, e);
        }
    }

    private @Nullable String gitHubDigest(String baseUrl, String version, NativePlatform platform) {
        if (!stripTrailingSlash(baseUrl).equals(GITHUB_RELEASES)) {
            logger.warn("[HeadlessMc] Cannot verify the executable downloaded from {}, set nativeSha256.", baseUrl);
            return null;
        }

        String url = GITHUB_API_RELEASES + version;
        try {
            HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(url))
                    .header("Accept", "application/vnd.github+json")
                    .timeout(Duration.ofMinutes(1))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 200) {
                String digest = findDigest(response.body(), platform.getAssetName());
                if (digest != null) {
                    return digest;
                }
            }

            logger.warn("[HeadlessMc] Could not get the SHA-256 hash of {} from {} (HTTP {}),"
                + " the download cannot be verified, set nativeSha256.", platform.getAssetName(), url, response.statusCode());
        } catch (IOException e) {
            logger.warn("[HeadlessMc] Could not get the SHA-256 hash of {} from {}: {},"
                + " the download cannot be verified, set nativeSha256.", platform.getAssetName(), url, e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GradleException("Interrupted while requesting " + url, e);
        }

        return null;
    }

    /**
     * @param releaseJson the json of a release from the GitHub API.
     * @param assetName the name of the asset.
     * @return the SHA-256 digest of the asset, or {@code null} if the json does not contain it.
     */
    static @Nullable String findDigest(String releaseJson, String assetName) {
        Matcher name = Pattern.compile("\"name\"\\s*:\\s*\"" + Pattern.quote(assetName) + "\"").matcher(releaseJson);
        if (!name.find()) {
            return null;
        }

        // browser_download_url is the last field of every asset
        int end = releaseJson.indexOf("\"browser_download_url\"", name.end());
        String asset = releaseJson.substring(name.end(), end < 0 ? releaseJson.length() : end);
        Matcher digest = DIGEST.matcher(asset);
        return digest.find() ? digest.group(1).toLowerCase(Locale.ENGLISH) : null;
    }

    static String sha256(Path file) {
        try (InputStream inputStream = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }

            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to hash " + file, e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void makeExecutable(Path executable) {
        if (!Files.isExecutable(executable) && !executable.toFile().setExecutable(true)) {
            throw new GradleException("Failed to make " + executable + " executable");
        }
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

}
