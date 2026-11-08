package io.github.headlesshq.headlessmc.gradle.internal

import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class ProjectScopeTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun directoriesInsideTheProjectAreAllowed() {
        val project = tempDir.resolve("project")
        val scope = ProjectScope(project.toFile())
        val checked = scope.check("dir", project.resolve("run/headlessmc/launcher").toFile())
        assertEquals(scope.root.resolve("run/headlessmc/launcher"), checked)
        assertDoesNotThrow { scope.check("dir", project.resolve("a/../b").toFile()) }
    }

    @Test
    fun directoriesOutsideTheProjectAreRejected() {
        val project = tempDir.resolve("project")
        val scope = ProjectScope(project.toFile())
        val e = assertThrows<GradleException> { scope.check("mcDirectory", tempDir.resolve("other").toFile()) }
        assertTrue(e.message!!.contains("mcDirectory"), e.message)
        assertThrows<GradleException> { scope.check("dir", project.resolve("../escape").toFile()) }
        assertThrows<GradleException> { scope.check("dir", tempDir.resolve("project-sibling").toFile()) }
        assertThrows<GradleException> { scope.check("dir", File(System.getProperty("user.home"), ".minecraft")) }
    }

    @Test
    fun theProjectDirectoryItselfIsRejected() {
        val project = tempDir.resolve("project")
        assertThrows<GradleException> { ProjectScope(project.toFile()).check("dir", project.toFile()) }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun symlinksPointingOutsideAreRejected() {
        val project = Files.createDirectories(tempDir.resolve("project"))
        val outside = Files.createDirectories(tempDir.resolve("outside"))
        Files.createSymbolicLink(project.resolve("link"), outside)
        val scope = ProjectScope(project.toFile())
        assertThrows<GradleException> { scope.check("dir", project.resolve("link/mc").toFile()) }
    }

    @Test
    fun managedConfigKeysAreRejected() {
        assertDoesNotThrow { ProjectScope.checkConfig("test", mapOf("hmc.assets.dummy" to "true")) }
        for (key in listOf("hmc.files.location", "hmc.files.mc", "hmc.files.game", "hmc.mcdir", "hmc.gamedir",
            "HMC.FILES.LOCATION", " hmc.files.mc ")) {
            val e = assertThrows<GradleException> { ProjectScope.checkConfig("test", mapOf(key to "/tmp")) }
            assertTrue(e.message!!.contains(key), e.message)
        }
    }

    @Test
    fun managedSystemPropertiesInJvmArgsAreRejected() {
        assertDoesNotThrow { ProjectScope.checkJvmArgs("test", listOf("-Xmx2G", "-Dhmc.assets.dummy=true", "-Dflag")) }
        assertThrows<GradleException> { ProjectScope.checkJvmArgs("test", listOf("-Dhmc.files.location=/tmp")) }
        assertThrows<GradleException> { ProjectScope.checkJvmArgs("test", listOf("-Dhmc.mcdir")) }
    }
}
