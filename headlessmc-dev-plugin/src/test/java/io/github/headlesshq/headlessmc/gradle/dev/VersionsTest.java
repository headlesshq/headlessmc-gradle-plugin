package io.github.headlesshq.headlessmc.gradle.dev;

import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionsTest {
    @Test
    void major() {
        assertEquals("3", Versions.major("3.0.0-RC8"));
        assertEquals("1", Versions.major("1"));
        assertEquals("33", Versions.major("33.4.0-jre"));
    }

    @Test
    void compare() {
        assertTrue(Versions.compare("1.2.10", "1.2.9") > 0);
        assertTrue(Versions.compare("1.2", "1.2.0") < 0);
        assertEquals(0, Versions.compare("3.0.0-RC8", "3.0.0-RC8"));
        assertTrue(Versions.compare("3.0.0-RC9", "3.0.0-RC8") > 0);
        assertTrue(Versions.compare("2.0", "10.0") < 0);
        // too long for a long, compared as strings
        assertTrue(Versions.compare("1.99999999999999999999", "1.99999999999999999998") > 0);
    }

    @Test
    void checkCoreVersion() {
        assertDoesNotThrow(() -> HeadlessMcDevPlugin.checkCoreVersion("a:b", "3.21.0", "3.21.0"));
        // older or newer minor versions work, HeadlessMc's version is used
        assertDoesNotThrow(() -> HeadlessMcDevPlugin.checkCoreVersion("a:b", "3.20.0", "3.21.0"));
        assertDoesNotThrow(() -> HeadlessMcDevPlugin.checkCoreVersion("a:b", "3.22.0", "3.21.0"));
        GradleException e = assertThrows(GradleException.class,
            () -> HeadlessMcDevPlugin.checkCoreVersion("a:b", "4.0.0", "3.21.0"));
        assertTrue(e.getMessage().contains("plugins cannot replace libraries of HeadlessMc"), e.getMessage());
    }

}
