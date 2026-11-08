package io.github.headlesshq.headlessmc.gradle.internal;

import io.github.headlesshq.headlessmc.gradle.HmcPlatform;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds the arguments for the HeadlessMc {@code launch} command.
 *
 * @param server              if a server is launched instead of the client.
 * @param platform            the platform, e.g. fabric.
 * @param minecraftVersion    the Minecraft version, e.g. 1.21.4.
 * @param loaderVersion       the version of the platform, e.g. the fabric loader version.
 * @param headless            if the client is launched headless.
 * @param offline             if an offline account is used.
 * @param acceptEula          if the server EULA is accepted.
 * @param jvmArgs             arguments for the game JVM.
 * @param gameArgs            arguments for the game.
 * @param joinServer          the address of a server the client joins.
 * @param resolution          the resolution, e.g. 800x600.
 * @param retries             how often launching is retried.
 * @param patchers            the patchers to use.
 * @param extraArgs           more arguments, added before the version.
 */
public record LaunchArgs(
    boolean server,
    String platform,
    String minecraftVersion,
    @Nullable String loaderVersion,
    boolean headless,
    boolean offline,
    boolean acceptEula,
    List<String> jvmArgs,
    List<String> gameArgs,
    @Nullable String joinServer,
    @Nullable String resolution,
    @Nullable Integer retries,
    List<String> patchers,
    List<String> extraArgs
) {
    /**
     * @return the version arguments, e.g. {@code [server, fabric, 1.21.4, 0.16.14]}, used by launch and mod add.
     */
    public List<String> versionArgs() {
        List<String> args = new ArrayList<>(4);
        if (server) {
            args.add("server");
        }

        String platform = this.platform.toLowerCase(Locale.ENGLISH);
        if (!HmcPlatform.VANILLA.equals(platform) || loaderVersion != null) {
            args.add(platform);
        }

        args.add(minecraftVersion);
        if (loaderVersion != null) {
            args.add(loaderVersion);
        }

        return args;
    }

    /**
     * @return the version arguments for {@code mod add}. HeadlessMc can only resolve a version with a build
     * (loader version) once it is installed, so clients leave the build out, the game directory is the same.
     * Servers keep it, because HeadlessMc uses a different directory for every server version.
     */
    public List<String> modVersionArgs() {
        if (server || loaderVersion == null) {
            return versionArgs();
        }

        return new LaunchArgs(false, platform, minecraftVersion, null, headless, offline, acceptEula,
            jvmArgs, gameArgs, joinServer, resolution, retries, patchers, extraArgs).versionArgs();
    }

    /**
     * @return the arguments for HeadlessMc, starting with {@code launch}.
     */
    public List<String> toArgs() {
        List<String> args = new ArrayList<>();
        args.add("launch");
        if (headless) {
            args.add("--headless");
        }

        if (offline) {
            args.add("--offline");
        }

        if (acceptEula) {
            args.add("--eula-accept");
        }

        if (!jvmArgs.isEmpty()) {
            args.add("--jvm");
            args.add(Args.join(jvmArgs));
        }

        if (!gameArgs.isEmpty()) {
            args.add("--game");
            args.add(Args.join(gameArgs));
        }

        if (joinServer != null) {
            args.add("--server");
            args.add(joinServer);
        }

        if (resolution != null) {
            args.add("--resolution");
            args.add(resolution);
        }

        if (retries != null) {
            args.add("--retries");
            args.add(retries.toString());
        }

        if (!patchers.isEmpty()) {
            args.add("--patchers");
            args.add(String.join(",", patchers));
        }

        args.addAll(extraArgs);
        args.addAll(versionArgs());
        return args;
    }

    /**
     * @return the name of the directory HeadlessMc uses for this server, relative to the server directory.
     * Mirrors {@code GameDirService}, which uses {@code "server-" + versionArg.toString("-")}.
     */
    public String serverDirectoryName() {
        List<String> args = new ArrayList<>(versionArgs());
        if (!server) {
            args.add(0, "server");
        }

        String platform = this.platform.toLowerCase(Locale.ENGLISH);
        if (HmcPlatform.VANILLA.equals(platform) && args.size() > 1 && args.get(1).equals(platform)) {
            args.remove(1);
        }

        return "server-" + String.join("-", args);
    }

}
