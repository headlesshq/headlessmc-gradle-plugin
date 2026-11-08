package io.github.headlesshq.headlessmc.gradle.internal;

import com.sun.net.httpserver.HttpServer;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logging;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeDownloaderTest {
    private static final byte[] CONTENT = "#!/bin/sh\necho headlessmc\n".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path tempDir;

    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    private String baseUrl;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/download/3.0.0-RC8/headlessmc-launcher-linux-x64", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(200, CONTENT.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(CONTENT);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/download";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private static String sha256() throws Exception {
        Path file = Files.createTempFile("content", "");
        Files.write(file, CONTENT);
        String hash = NativeDownloader.sha256(file);
        Files.delete(file);
        return hash;
    }

    @Test
    void downloadsAndVerifies() throws Exception {
        Path executable = tempDir.resolve("native/3.0.0-RC8/headlessmc-launcher-linux-x64");
        NativeDownloader downloader = new NativeDownloader(Logging.getLogger(NativeDownloaderTest.class));
        downloader.ensure(executable, baseUrl, "3.0.0-RC8", NativePlatform.LINUX_X64, sha256(), false);
        assertTrue(Files.isRegularFile(executable));
        assertTrue(Files.isExecutable(executable));
        assertEquals(1, requests.get());
        // already downloaded
        downloader.ensure(executable, baseUrl + "/", "3.0.0-RC8", NativePlatform.LINUX_X64, sha256().toUpperCase(), false);
        assertEquals(1, requests.get());
    }

    @Test
    void wrongHashFailsAndLeavesNothingBehind() {
        Path executable = tempDir.resolve("headlessmc-launcher-linux-x64");
        NativeDownloader downloader = new NativeDownloader(Logging.getLogger(NativeDownloaderTest.class));
        GradleException e = assertThrows(GradleException.class, () -> downloader.ensure(
            executable, baseUrl, "3.0.0-RC8", NativePlatform.LINUX_X64, "0".repeat(64), false));
        assertTrue(e.getMessage().contains("SHA-256"), e.getMessage());
        assertFalse(Files.exists(executable));
        assertFalse(Files.exists(tempDir.resolve("headlessmc-launcher-linux-x64.part")));
    }

    @Test
    void existingFileWithWrongHashIsDownloadedAgain() throws Exception {
        Path executable = tempDir.resolve("headlessmc-launcher-linux-x64");
        Files.writeString(executable, "corrupted");
        new NativeDownloader(Logging.getLogger(NativeDownloaderTest.class))
            .ensure(executable, baseUrl, "3.0.0-RC8", NativePlatform.LINUX_X64, sha256(), false);
        assertEquals(1, requests.get());
        assertEquals(new String(CONTENT, StandardCharsets.UTF_8), Files.readString(executable));
    }

    @Test
    void missingAssetFails() {
        Path executable = tempDir.resolve("x");
        NativeDownloader downloader = new NativeDownloader(Logging.getLogger(NativeDownloaderTest.class));
        GradleException e = assertThrows(GradleException.class, () -> downloader.ensure(
            executable, baseUrl, "3.0.0-RC1", NativePlatform.LINUX_X64, sha256(), false));
        assertTrue(e.getMessage().contains("404"), e.getMessage());
    }

    @Test
    void offlineFailsIfNotDownloaded() {
        NativeDownloader downloader = new NativeDownloader(Logging.getLogger(NativeDownloaderTest.class));
        GradleException e = assertThrows(GradleException.class, () -> downloader.ensure(
            tempDir.resolve("x"), baseUrl, "3.0.0-RC8", NativePlatform.LINUX_X64, null, true));
        assertTrue(e.getMessage().contains("offline"), e.getMessage());
        assertEquals(0, requests.get());
    }

    @Test
    void invalidHashIsRejected() {
        NativeDownloader downloader = new NativeDownloader(Logging.getLogger(NativeDownloaderTest.class));
        assertThrows(GradleException.class, () -> downloader.ensure(
            tempDir.resolve("x"), baseUrl, "3.0.0-RC8", NativePlatform.LINUX_X64, "not-a-hash", false));
    }

    @Test
    void findsDigestInGitHubReleaseJson() {
        String json = "{\"assets\":[{\"name\":\"headlessmc.jar\",\"uploader\":{\"login\":\"bot\"},"
            + "\"digest\":\"sha256:" + "a".repeat(64) + "\",\"browser_download_url\":\"x\"},"
            + "{\"name\": \"headlessmc-launcher-linux-x64\",\"uploader\":{\"login\":\"bot\"},"
            + "\"size\":1,\"digest\": \"sha256:" + "B".repeat(64) + "\",\"browser_download_url\":\"y\"},"
            + "{\"name\":\"headlessmc-launcher-macos-x64\",\"browser_download_url\":\"z\"}]}";
        assertEquals("a".repeat(64), NativeDownloader.findDigest(json, "headlessmc.jar"));
        assertEquals("b".repeat(64), NativeDownloader.findDigest(json, "headlessmc-launcher-linux-x64"));
        // no digest for this asset, must not take the digest of another asset
        assertNull(NativeDownloader.findDigest(json, "headlessmc-launcher-macos-x64"));
        assertNull(NativeDownloader.findDigest(json, "headlessmc-launcher-windows-x64.exe"));
    }

}
