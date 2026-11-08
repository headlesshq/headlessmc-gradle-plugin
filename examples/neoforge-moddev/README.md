# NeoForge mod with ModDevGradle (Kotlin DSL)

`tasks.jar` already is the release jar, so it is copied into the mods folder directly.

```shell
./gradlew runHeadlessMc       # with a window, log in first: ./gradlew headlessmc --args="login"
./gradlew runHeadlessMcTest   # headless, offline, with mc-runtime-test
```
