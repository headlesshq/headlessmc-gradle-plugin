# Examples

Every example is a standalone Gradle build which uses the plugins of this repository through
`includeBuild("../..")`. Run them with the wrapper of this repository, e.g.

```shell
cd examples/vanilla-kotlin
../../gradlew runVanilla
```

| Example                                    | Shows                                                                         |
|--------------------------------------------|-------------------------------------------------------------------------------|
| [vanilla-kotlin](vanilla-kotlin)           | the smallest setup, Kotlin DSL, headless/offline/dummy assets                 |
| [fabric-loom](fabric-loom)                 | a Fabric mod: `from(remapJar)`, Fabric API, mc-runtime-test, a CI workflow     |
| [neoforge-moddev](neoforge-moddev)         | a NeoForge mod with ModDevGradle, Kotlin DSL                                  |
| [version-matrix](version-matrix)           | run tasks for many versions and platforms in a loop                           |
| [paper-server](paper-server)               | Paper and Fabric servers                                                      |
| [commands-and-config](commands-and-config) | `./gradlew headlessmc --args=...`, `HeadlessMcExec`, configuration, Modrinth  |
| [native-executable](native-executable)     | HeadlessMc's native executable instead of Java 25                             |
| [local-headlessmc](local-headlessmc)       | running your own HeadlessMc build                                             |
| [hmc-plugin](hmc-plugin)                   | writing a HeadlessMc plugin and running it                                    |
| [multi-project](multi-project)             | a HeadlessMc plugin and a mod that uses it, in one build                      |

The functional tests (`ExamplesTest`) configure every example without third-party plugins.
