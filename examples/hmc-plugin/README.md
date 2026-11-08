# A HeadlessMc plugin

The [plugin template](https://github.com/headlesshq/headlessmc-plugin-template) with the
`io.github.headlesshq.hmc-plugin-gradle-plugin`. It adds the `hello` command, `hello --banner` uses jfiglet,
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

Plugins need HeadlessMc 3.0.0-RC9, which is not released yet. Until then, the `hmc-plugin-gradle-plugin` uses
`io.github.headlesshq:headlessmc-application:3.0.0-RC9-SNAPSHOT`, plugins built against it are compatible with
`[3.0.0-RC9,4)`. Either publish the `feat/plugins` branch of HeadlessMc to Maven Local, or include it as a git
submodule like the [plugin template](https://github.com/headlesshq/headlessmc-plugin-template) does
(`headlessmc.includedBuild`, see the [README](../../README.md#writing-headlessmc-plugins)).

```shell
# in the headlessmc repository, branch feat/plugins
./gradlew publishToMavenLocal -Pversion=3.0.0-RC9-SNAPSHOT
./gradlew :headlessmc-application:quarkusBuild :headlessmc-application:mutableJarZip -PmutableJar -Pversion=3.0.0-RC9-SNAPSHOT
# here
./gradlew hello \
  -PheadlessmcJar=../../../headlessmc/headlessmc-application/build/quarkus-app/quarkus-run.jar \
  -PheadlessmcMutableJar=../../../headlessmc/headlessmc-application/build/headlessmc-mutable.zip
```
