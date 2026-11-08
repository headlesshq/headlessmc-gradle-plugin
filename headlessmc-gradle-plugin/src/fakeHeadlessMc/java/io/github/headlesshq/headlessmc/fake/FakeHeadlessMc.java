package io.github.headlesshq.headlessmc.fake;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Stands in for HeadlessMc in the functional tests.
 * Like HeadlessMc it parses -Dkey=value system properties from the start of its arguments
 * (the native executable has to do this), then records every invocation as a properties file
 * in {@code <hmc.files.location>/fake-invocations}.
 * {@code exit <code>} exits with the given code.
 */
public final class FakeHeadlessMc {
    private FakeHeadlessMc() {
    }

    public static void main(String[] rawArgs) throws IOException {
        List<String> args = new ArrayList<>();
        List<String> parsedProperties = new ArrayList<>();
        boolean argsStarted = false;
        for (String arg : rawArgs) {
            if (!argsStarted && arg.startsWith("-D") && arg.contains("=")) {
                int index = arg.indexOf('=');
                System.setProperty(arg.substring(2, index), arg.substring(index + 1));
                parsedProperties.add(arg.substring(2, index));
                continue;
            }

            argsStarted = true;
            args.add(arg);
        }

        String location = System.getProperty("hmc.files.location");
        if (location == null) {
            System.err.println("hmc.files.location is not set!");
            System.exit(42);
        }

        Path invocations = Paths.get(location).resolve("fake-invocations");
        Files.createDirectories(invocations);
        // unique and ordered, processes might run in parallel
        java.time.Instant now = java.time.Instant.now();
        String name = String.format("%d%09d-%d.properties", now.getEpochSecond(), now.getNano(), ProcessHandle.current().pid());

        Properties properties = new Properties();
        properties.setProperty("args", String.join("\u001f", args));
        properties.setProperty("argCount", Integer.toString(args.size()));
        properties.setProperty("mode", Boolean.getBoolean("fake.native") ? "native" : "jvm");
        properties.setProperty("parsedProperties", String.join(",", parsedProperties));
        properties.setProperty("java.specification.version", System.getProperty("java.specification.version"));
        properties.setProperty("cwd", Paths.get("").toAbsolutePath().toString());
        new TreeMap<>(System.getProperties()).forEach((key, value) -> {
            if (key.toString().startsWith("hmc.")) {
                properties.setProperty("property." + key, value.toString());
            }
        });

        try (OutputStream out = Files.newOutputStream(invocations.resolve(name))) {
            properties.store(out, null);
        }

        System.out.println("[FakeHeadlessMc] " + String.join(" ", args));
        if (args.size() == 2 && args.get(0).equals("exit")) {
            System.exit(Integer.parseInt(args.get(1)));
        }

        if (!args.isEmpty() && args.get(0).equals("launch")) {
            Path game = Paths.get(System.getProperty("hmc.files.game", location));
            if (Files.isDirectory(game.resolve("mods"))) {
                try (var stream = Files.list(game.resolve("mods"))) {
                    stream.sorted().forEach(mod -> System.out.println("[FakeHeadlessMc] mod " + mod.getFileName()));
                }
            }
        }

        System.out.flush();
        System.out.write(new byte[0]);
        System.out.println(new String("done".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
    }

}
