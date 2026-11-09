package io.github.headlesshq.headlessmc.gradle;

import com.sun.net.httpserver.HttpServer;
import io.github.headlesshq.headlessmc.gradle.internal.NativePlatform;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistributionTest extends FunctionalTestBase {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * A shell script standing in for the native executable, runs the fake HeadlessMc,
     * which has to parse the system properties from its arguments, like the real native executable.
     */
    private static String nativeScript() {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        return "#!/bin/sh\nexec \"" + java + "\" -Dfake.native=true -jar \"" + path(FAKE_JAR) + "\" \"$@\"\n";
    }

    private void writeBuild(String headlessmc) {
        write("settings.gradle", "rootProject.name = 'test-project'\n");
        write("build.gradle", "plugins {\n    id 'io.github.headlesshq.headlessmc'\n}\n\n"
            + "headlessmc {\n" + headlessmc + "\n}\n"
            + "tasks.register('runClient', HeadlessMcRunTask) { minecraftVersion = '1.21.4' }\n");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void customNativeExecutable() throws Exception {
        write("hmc/headlessmc", nativeScript());
        assertTrue(file("hmc/headlessmc").toFile().setExecutable(true));
        writeBuild("    executable = file('hmc/headlessmc')");
        build("runClient");
        Invocation invocation = invocations().get(0);
        assertEquals("native", invocation.mode());
        assertEquals(List.of("launch", "1.21.4"), invocation.args());
        // the native executable gets the configuration as -D arguments before the actual arguments
        assertTrue(invocation.parsedProperties().contains("hmc.files.location"), invocation.parsedProperties());
        assertTrue(invocation.parsedProperties().contains("hmc.files.mc"), invocation.parsedProperties());
        assertTrue(invocation.parsedProperties().contains("hmc.files.game"), invocation.parsedProperties());
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void autoFallsBackToTheNativeExecutableWithoutJava25() throws Exception {
        NativePlatform platform = NativePlatform.current();
        assertNotNull(platform);
        byte[] script = nativeScript().getBytes(StandardCharsets.UTF_8);
        AtomicInteger downloads = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/releases/3.0.0-RC8/" + platform.getAssetName(), exchange -> {
            downloads.incrementAndGet();
            exchange.sendResponseHeaders(200, script.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(script);
            }
        });
        server.start();
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(script));
        writeBuild("    version = '3.0.0-RC8'\n"
            + "    javaVersion = 99\n"
            + "    nativeDownloadUrl = 'http://127.0.0.1:" + server.getAddress().getPort() + "/releases'\n"
            + "    nativeSha256 = '" + sha256 + "'\n");
        build("runClient");
        build("runClient");
        assertEquals(1, downloads.get(), "the executable is only downloaded once");
        assertTrue(Files.isExecutable(file("run/headlessmc/native/3.0.0-RC8/" + platform.getAssetName())));
        List<Invocation> invocations = invocations();
        assertEquals(2, invocations.size());
        assertEquals("native", invocations.get(0).mode());
    }

    @Test
    void jvmFailsWithoutJava25() {
        writeBuild("    distribution = HmcDistribution.JVM\n    javaVersion = 99");
        write("build.gradle", Files.exists(file("build.gradle")) ? read("build.gradle") + fakeDependency() : "");
        BuildResult result = buildAndFail("runClient");
        assertTrue(result.getOutput().contains("Cannot run HeadlessMc on the JVM: no Java toolchain"), result.getOutput());
    }

    @Test
    void jvmWithOldJavaFails() {
        writeBuild("    distribution = HmcDistribution.JVM\n"
            + "    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(" + oldJava() + ") }");
        write("build.gradle", read("build.gradle") + fakeDependency());
        BuildResult result = runner("runClient").buildAndFail();
        assertTrue(result.getOutput().contains("HeadlessMc requires Java 25") || result.getOutput().contains("No matching toolchains"),
            result.getOutput());
    }

    @Test
    void jvmFailsIfTheJarCannotBeResolved() {
        // no repositories
        writeBuild("    distribution = HmcDistribution.JVM");
        BuildResult result = buildAndFail("runClient");
        assertTrue(result.getOutput().contains("io.github.headlesshq:headlessmc-application:3.0.0-RC8:runner could not be resolved"),
            result.getOutput());
        assertTrue(result.getOutput().contains("mavenCentral()"), result.getOutput());
    }

    @Test
    void defaultRuntimeDependency() {
        writeBuild("");
        write("build.gradle", read("build.gradle")
            + "tasks.register('printRuntime') {\n"
            + "    def result = configurations.headlessmcRuntime.incoming.resolutionResult\n"
            + "    doLast { result.root.dependencies.each { println \"RUNTIME ${it.requested.displayName}\" } }\n"
            + "}\n");
        BuildResult result = build("printRuntime");
        assertTrue(result.getOutput().contains("RUNTIME io.github.headlesshq:headlessmc-application:3.0.0-RC8"),
            result.getOutput());
    }

    @Test
    void versionChangesTheRuntimeDependency() {
        writeBuild("    version = '3.0.0-RC7'");
        write("build.gradle", read("build.gradle")
            + "tasks.register('printRuntime') {\n"
            + "    def result = configurations.headlessmcRuntime.incoming.resolutionResult\n"
            + "    doLast { result.root.dependencies.each { println \"RUNTIME ${it.requested.displayName}\" } }\n"
            + "}\n");
        assertTrue(build("printRuntime").getOutput().contains("RUNTIME io.github.headlesshq:headlessmc-application:3.0.0-RC7"));
    }

    @Test
    void groupChangesTheRuntimeDependency() {
        writeBuild("    group = 'com.github.headlesshq.headlessmc'\n    version = 'abcdef1234'");
        write("build.gradle", read("build.gradle")
            + "tasks.register('printRuntime') {\n"
            + "    def result = configurations.headlessmcRuntime.incoming.resolutionResult\n"
            + "    doLast { result.root.dependencies.each { println \"RUNTIME ${it.requested.displayName}\" } }\n"
            + "}\n");
        BuildResult result = build("printRuntime");
        assertTrue(result.getOutput().contains("RUNTIME com.github.headlesshq.headlessmc:headlessmc-application:abcdef1234"),
            result.getOutput());
    }

    @Test
    void jitPackBuildsDoNotFallBackToANativeExecutable() {
        // no repositories, a JitPack build has no native executable to fall back to
        writeBuild("    group = 'com.github.headlesshq.headlessmc'\n    version = 'abcdef1234'");
        BuildResult result = buildAndFail("runClient");
        assertTrue(result.getOutput().contains("there are only native executables of releases"), result.getOutput());
        assertTrue(result.getOutput().contains("com.github.headlesshq.headlessmc:headlessmc-application:abcdef1234:runner could not be resolved"),
            result.getOutput());
        assertTrue(result.getOutput().contains("https://jitpack.io"), result.getOutput());
        assertFalse(Files.exists(file("run/headlessmc/native")), "nothing is downloaded");
    }

    private static String fakeDependency() {
        return "\ndependencies { headlessmcRuntime files('" + path(FAKE_JAR) + "') }\n";
    }

    private static int oldJava() {
        return 17;
    }

    private String read(String path) {
        try {
            return Files.readString(file(path));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

}
