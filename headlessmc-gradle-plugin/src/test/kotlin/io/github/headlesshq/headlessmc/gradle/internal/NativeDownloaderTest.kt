package io.github.headlesshq.headlessmc.gradle.internal

import com.sun.net.httpserver.HttpServer
import org.gradle.api.GradleException
import org.gradle.api.logging.Logging
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class NativeDownloaderTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var server: HttpServer
    private val requests = AtomicInteger()
    private lateinit var baseUrl: String
    private val downloader = NativeDownloader(Logging.getLogger(NativeDownloaderTest::class.java))

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/download/3.0.0-RC8/headlessmc-launcher-linux-x64") { exchange ->
            requests.incrementAndGet()
            exchange.sendResponseHeaders(200, CONTENT.size.toLong())
            exchange.responseBody.use { it.write(CONTENT) }
        }
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}/download"
    }

    @AfterEach
    fun stopServer() {
        server.stop(0)
    }

    private fun sha256(): String {
        val file = Files.createTempFile("content", "")
        Files.write(file, CONTENT)
        return NativeDownloader.sha256(file).also { Files.delete(file) }
    }

    @Test
    fun downloadsAndVerifies() {
        val executable = tempDir.resolve("native/3.0.0-RC8/headlessmc-launcher-linux-x64")
        downloader.ensure(executable, baseUrl, "3.0.0-RC8", NativePlatform.LINUX_X64, sha256(), false)
        assertTrue(Files.isRegularFile(executable))
        assertTrue(Files.isExecutable(executable))
        assertEquals(1, requests.get())
        // already downloaded
        downloader.ensure(executable, "$baseUrl/", "3.0.0-RC8", NativePlatform.LINUX_X64, sha256().uppercase(), false)
        assertEquals(1, requests.get())
    }

    @Test
    fun wrongHashFailsAndLeavesNothingBehind() {
        val executable = tempDir.resolve("headlessmc-launcher-linux-x64")
        val e = assertThrows<GradleException> {
            downloader.ensure(executable, baseUrl, "3.0.0-RC8", NativePlatform.LINUX_X64, "0".repeat(64), false)
        }
        assertTrue(e.message!!.contains("SHA-256"), e.message)
        assertFalse(Files.exists(executable))
        assertFalse(Files.exists(tempDir.resolve("headlessmc-launcher-linux-x64.part")))
    }

    @Test
    fun existingFileWithWrongHashIsDownloadedAgain() {
        val executable = tempDir.resolve("headlessmc-launcher-linux-x64")
        Files.writeString(executable, "corrupted")
        downloader.ensure(executable, baseUrl, "3.0.0-RC8", NativePlatform.LINUX_X64, sha256(), false)
        assertEquals(1, requests.get())
        assertEquals(String(CONTENT), Files.readString(executable))
    }

    @Test
    fun missingAssetFails() {
        val e = assertThrows<GradleException> {
            downloader.ensure(tempDir.resolve("x"), baseUrl, "3.0.0-RC1", NativePlatform.LINUX_X64, sha256(), false)
        }
        assertTrue(e.message!!.contains("404"), e.message)
    }

    @Test
    fun offlineFailsIfNotDownloaded() {
        val e = assertThrows<GradleException> {
            downloader.ensure(tempDir.resolve("x"), baseUrl, "3.0.0-RC8", NativePlatform.LINUX_X64, null, true)
        }
        assertTrue(e.message!!.contains("offline"), e.message)
        assertEquals(0, requests.get())
    }

    @Test
    fun invalidHashIsRejected() {
        assertThrows<GradleException> {
            downloader.ensure(tempDir.resolve("x"), baseUrl, "3.0.0-RC8", NativePlatform.LINUX_X64, "not-a-hash", false)
        }
    }

    @Test
    fun findsDigestInGitHubReleaseJson() {
        val json = "{\"assets\":[{\"name\":\"headlessmc.jar\",\"uploader\":{\"login\":\"bot\"}," +
            "\"digest\":\"sha256:" + "a".repeat(64) + "\",\"browser_download_url\":\"x\"}," +
            "{\"name\": \"headlessmc-launcher-linux-x64\",\"uploader\":{\"login\":\"bot\"}," +
            "\"size\":1,\"digest\": \"sha256:" + "B".repeat(64) + "\",\"browser_download_url\":\"y\"}," +
            "{\"name\":\"headlessmc-launcher-macos-x64\",\"browser_download_url\":\"z\"}]}"
        assertEquals("a".repeat(64), NativeDownloader.findDigest(json, "headlessmc.jar"))
        assertEquals("b".repeat(64), NativeDownloader.findDigest(json, "headlessmc-launcher-linux-x64"))
        // no digest for this asset, must not take the digest of another asset
        assertNull(NativeDownloader.findDigest(json, "headlessmc-launcher-macos-x64"))
        assertNull(NativeDownloader.findDigest(json, "headlessmc-launcher-windows-x64.exe"))
    }

    private companion object {
        val CONTENT: ByteArray = "#!/bin/sh\necho headlessmc\n".toByteArray()
    }
}
