package io.github.headlesshq.headlessmc.gradle.internal;

import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectScopeTest {
    @TempDir
    Path tempDir;

    @Test
    void directoriesInsideTheProjectAreAllowed() {
        Path project = tempDir.resolve("project");
        ProjectScope scope = new ProjectScope(project.toFile());
        Path checked = scope.check("dir", project.resolve("run/headlessmc/launcher").toFile());
        assertEquals(scope.getRoot().resolve("run/headlessmc/launcher"), checked);
        assertDoesNotThrow(() -> scope.check("dir", project.resolve("a/../b").toFile()));
    }

    @Test
    void directoriesOutsideTheProjectAreRejected() {
        Path project = tempDir.resolve("project");
        ProjectScope scope = new ProjectScope(project.toFile());
        GradleException e = assertThrows(GradleException.class,
            () -> scope.check("mcDirectory", tempDir.resolve("other").toFile()));
        assertTrue(e.getMessage().contains("mcDirectory"), e.getMessage());
        assertThrows(GradleException.class, () -> scope.check("dir", project.resolve("../escape").toFile()));
        assertThrows(GradleException.class, () -> scope.check("dir", tempDir.resolve("project-sibling").toFile()));
        assertThrows(GradleException.class, () -> scope.check("dir", new File(System.getProperty("user.home"), ".minecraft")));
    }

    @Test
    void theProjectDirectoryItselfIsRejected() {
        Path project = tempDir.resolve("project");
        ProjectScope scope = new ProjectScope(project.toFile());
        assertThrows(GradleException.class, () -> scope.check("dir", project.toFile()));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void symlinksPointingOutsideAreRejected() throws Exception {
        Path project = Files.createDirectories(tempDir.resolve("project"));
        Path outside = Files.createDirectories(tempDir.resolve("outside"));
        Files.createSymbolicLink(project.resolve("link"), outside);
        ProjectScope scope = new ProjectScope(project.toFile());
        assertThrows(GradleException.class, () -> scope.check("dir", project.resolve("link/mc").toFile()));
    }

    @Test
    void managedConfigKeysAreRejected() {
        assertDoesNotThrow(() -> ProjectScope.checkConfig("test", Map.of("hmc.assets.dummy", "true")));
        for (String key : List.of("hmc.files.location", "hmc.files.mc", "hmc.files.game", "hmc.mcdir", "hmc.gamedir",
            "HMC.FILES.LOCATION", " hmc.files.mc ")) {
            GradleException e = assertThrows(GradleException.class, () -> ProjectScope.checkConfig("test", Map.of(key, "/tmp")));
            assertTrue(e.getMessage().contains(key), e.getMessage());
        }
    }

    @Test
    void managedSystemPropertiesInJvmArgsAreRejected() {
        assertDoesNotThrow(() -> ProjectScope.checkJvmArgs("test", List.of("-Xmx2G", "-Dhmc.assets.dummy=true", "-Dflag")));
        assertThrows(GradleException.class, () -> ProjectScope.checkJvmArgs("test", List.of("-Dhmc.files.location=/tmp")));
        assertThrows(GradleException.class, () -> ProjectScope.checkJvmArgs("test", List.of("-Dhmc.mcdir")));
    }

}
