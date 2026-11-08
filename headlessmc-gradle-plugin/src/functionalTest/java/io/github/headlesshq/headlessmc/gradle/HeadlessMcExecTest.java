package io.github.headlesshq.headlessmc.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadlessMcExecTest extends FunctionalTestBase {
    @Test
    void headlessmcTaskPassesArgs() {
        groovyBuild("");
        BuildResult result = build("headlessmc", "--args=version list");
        assertEquals(TaskOutcome.SUCCESS, result.task(":headlessmc").getOutcome());
        List<Invocation> invocations = invocations();
        assertEquals(1, invocations.size());
        assertEquals(List.of("version", "list"), invocations.get(0).args());
    }

    @Test
    void argsWithQuotes() {
        groovyBuild("");
        build("headlessmc", "--args=launch --jvm \"-Xmx2G -Da=b\" fabric 1.21.4");
        assertEquals(List.of("launch", "--jvm", "-Xmx2G -Da=b", "fabric", "1.21.4"), invocations().get(0).args());
    }

    @Test
    void everythingIsPlacedInsideTheProject() {
        groovyBuild("");
        build("headlessmc", "--args=debug");
        Invocation invocation = invocations().get(0);
        Path root = projectDir.toAbsolutePath();
        assertEquals(root.resolve("run/headlessmc/launcher").toString(), invocation.property("hmc.files.location"));
        assertEquals(root.resolve("run/headlessmc/mc").toString(), invocation.property("hmc.files.mc"));
        // the exec task has no game directory, HeadlessMc uses <mc>/<version>
        assertNull(invocation.property("hmc.files.game"));
        assertEquals("false", invocation.property("hmc.jline.enabled"));
        assertEquals(realPath(root), invocation.cwd());
    }

    @Test
    void runsOnJava25ByDefault() {
        groovyBuild("");
        build("headlessmc", "--args=debug");
        Invocation invocation = invocations().get(0);
        assertEquals("jvm", invocation.mode());
        assertEquals("25", invocation.javaVersion());
        // with the JVM the system properties are JVM arguments, not program arguments
        assertEquals("", invocation.parsedProperties());
    }

    @Test
    void customTaskWithMultipleArgs() {
        groovyBuild("tasks.register('listVersions', HeadlessMcExec) {\n"
            + "    args 'version', 'list'\n"
            + "    args '--verbose'\n"
            + "}\n");
        build("listVersions");
        assertEquals(List.of("version", "list", "--verbose"), invocations().get(0).args());
    }

    @Test
    void launcherConfigBecomesSystemProperties() {
        groovyBuild("headlessmc {\n"
            + "    launcherConfig.put('hmc.assets.dummy', 'true')\n"
            + "    launcherConfig.put('hmc.log.console-level', 'INFO')\n"
            + "}\n"
            + "tasks.named('headlessmc') {\n"
            + "    launcherConfig.put('hmc.java.download', 'false')\n"
            + "}\n");
        build("headlessmc", "--args=config ls");
        Invocation invocation = invocations().get(0);
        assertEquals("true", invocation.property("hmc.assets.dummy"));
        assertEquals("INFO", invocation.property("hmc.log.console-level"));
        assertEquals("false", invocation.property("hmc.java.download"));
    }

    @Test
    void jlineCanBeEnabled() {
        groovyBuild("headlessmc { jline = true }\n");
        build("headlessmc", "--args=debug");
        assertEquals("true", invocations().get(0).property("hmc.jline.enabled"));
    }

    @Test
    void nonZeroExitCodeFailsTheBuild() {
        groovyBuild("");
        BuildResult result = buildAndFail("headlessmc", "--args=exit 3");
        assertTrue(result.getOutput().contains("HeadlessMc exited with exit code 3"), result.getOutput());
    }

    @Test
    void ignoreExitValue() {
        groovyBuild("tasks.named('headlessmc') { ignoreExitValue = true }\n");
        build("headlessmc", "--args=exit 3");
    }

    @Test
    void kotlinDsl() {
        kotlinBuild("tasks.register<io.github.headlesshq.headlessmc.gradle.HeadlessMcExec>(\"hello\") {\n"
            + "    args(\"help\")\n"
            + "}\n");
        build("hello");
        assertEquals(List.of("help"), invocations().get(0).args());
    }

    @Test
    void neverUpToDate() {
        groovyBuild("");
        build("headlessmc", "--args=debug");
        BuildResult second = build("headlessmc", "--args=debug");
        assertEquals(TaskOutcome.SUCCESS, second.task(":headlessmc").getOutcome());
        assertEquals(2, invocations().size());
    }

    @Test
    void configurationCache() {
        groovyBuild("");
        BuildResult first = build("headlessmc", "--args=debug", "--configuration-cache");
        assertTrue(first.getOutput().contains("Configuration cache entry stored"), first.getOutput());
        BuildResult second = build("headlessmc", "--args=debug", "--configuration-cache");
        assertTrue(second.getOutput().contains("Configuration cache entry reused"), second.getOutput());
        assertEquals(2, invocations().size());
    }

    @Test
    void groovyCanUseTypesWithoutImports() {
        groovyBuild("assert HeadlessMcRunTask == io.github.headlesshq.headlessmc.gradle.HeadlessMcRunTask\n"
            + "assert HeadlessMcExec == io.github.headlesshq.headlessmc.gradle.HeadlessMcExec\n"
            + "assert HmcDistribution.NATIVE == io.github.headlesshq.headlessmc.gradle.HmcDistribution.NATIVE\n"
            + "assert HmcPlatform.FABRIC == 'fabric'\n");
        build("help");
    }

}
