# A local HeadlessMc build

The HeadlessMc the tasks run is just the `headlessmcRuntime` configuration (on the JVM) or
`headlessmc.executable` (native), both can point to your own build:

```shell
# in the headlessmc repository
./gradlew :headlessmc-application:quarkusBuild -PuberJar
# here
./gradlew runLocal -PheadlessmcJar=../../../headlessmc/headlessmc-application/build/headlessmc-application-3.0.0-RC9-SNAPSHOT-runner.jar
```

A SNAPSHOT published with `./gradlew publishToMavenLocal -PuberJar` works too, with `headlessmc.version` and
`mavenLocal()`. Even a local HeadlessMc only uses the directories inside this project.
