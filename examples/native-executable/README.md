# Native executable

HeadlessMc 3 runs on Java 25, which Gradle and your mod build might not use.
HeadlessMc is therefore always started as its own process:

| `headlessmc.distribution` | Runs                                                                                   |
|---------------------------|----------------------------------------------------------------------------------------|
| `AUTO` (default)          | `JVM` if a Java 25 toolchain is found, otherwise `NATIVE`                              |
| `JVM`                     | `io.github.headlesshq:headlessmc-application:<version>:runner` from Maven Central on Java 25 |
| `NATIVE`                  | the native executable of the GitHub release for your OS (Linux, macOS, Windows; x64/arm64) |

To get a Java 25 toolchain automatically, apply the
[foojay resolver](https://github.com/gradle/foojay-toolchains) in your `settings.gradle`:

```kotlin
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
```

```shell
./gradlew runNative
```
