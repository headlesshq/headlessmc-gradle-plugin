# A HeadlessMc plugin

The [plugin template](https://github.com/headlesshq/headlessmc-plugin-template) with the
`io.github.headlesshq.headlessmc-dev-plugin`. It adds the `hello` command, `hello --banner` uses jfiglet,
a library nested in the plugin jar.

```shell
./gradlew build                     # build/libs/hmc-plugin-1.0.0.jar
./gradlew headlessmc --args="plugin ls"
./gradlew headlessmc --args="hello --banner you"
./gradlew hello
```

Every HeadlessMc task of the project runs the HeadlessMc in `run/headlessmc` with the freshly built plugin
installed in `run/headlessmc/launcher/plugins`, so `~/.local/share/headlessmc/plugins` is never touched.
`./gradlew installPlugin` only installs it.

## Which HeadlessMc

Plugins need HeadlessMc 3.0.0-RC9 or newer. By default, the `headlessmc-dev-plugin` compiles against and runs
`io.github.headlesshq:headlessmc-application:3.0.0-RC9` from Maven Central, plugins built against it are compatible
with `[3.0.0-RC9,4)`. Set `headlessmc.version` for another release.

To run the plugin with a HeadlessMc you built yourself, pass its jar and its mutable jar, or include the HeadlessMc
repository as a build (`headlessmc.includedBuild`, e.g. a git submodule), which also compiles the plugin against it:

```shell
# in the headlessmc repository
./gradlew :headlessmc-application:quarkusBuild :headlessmc-application:mutableJarZip -PmutableJar
# here
./gradlew hello \
  -PheadlessmcJar=../../../headlessmc/headlessmc-application/build/quarkus-app/quarkus-run.jar \
  -PheadlessmcMutableJar=../../../headlessmc/headlessmc-application/build/headlessmc-mutable.zip
```

## Publishing to the plugin index

```shell
./gradlew generatePluginIndexEntry  # build/headlessmc-plugin/index-entry.json
```

writes the entry of `build/libs/hmc-plugin-1.0.0.jar` for the `index.json` of
[headlessmc-plugins](https://github.com/headlesshq/headlessmc-plugins), which `plugin search` and `plugin download`
read: the values of `headlessmcPlugin` and a single release with the sha256 and size of the jar. Its `url`
defaults to the jar attached to the GitHub release of the version, because the homepage is on github.com,
`https://github.com/headlesshq/headlessmc-gradle-plugin/releases/download/1.0.0/hmc-plugin-1.0.0.jar`. Set
`downloadUrl` for any other location and `icon` for an icon.

Upload the jar there, then add the entry to `plugins` of headlessmc-plugins' `index.json`, or, if the plugin is
already listed, append its single release to the `downloads` of the existing entry. See the
[README](../../README.md#publishing-to-the-plugin-index).
