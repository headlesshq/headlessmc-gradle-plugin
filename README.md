# HeadlessMc Gradle Plugins

Gradle plugins for [HeadlessMc](https://github.com/headlesshq/headlessmc), the command line launcher for Minecraft.

* **`io.github.headlesshq.headlessmc-gradle-plugin`** launches Minecraft with HeadlessMc.
  Your usual `runClient` works very differently from how players launch the game.
  Problems with the packaging of your release jar, mixins or mappings only show up with a "real" launcher.
  With this plugin you run your release jar, e.g. in CI, headless, together with
  [mc-runtime-test](https://github.com/headlesshq/mc-runtime-test).
* **`io.github.headlesshq.hmc-plugin-gradle-plugin`** builds [HeadlessMc plugins](https://headlesshq.github.io/headlessmc/plugins/)
  and runs them with HeadlessMc.

Both use HeadlessMc 3.0.0-RC8 by default and only ever let HeadlessMc place files inside your project.

## Setup

```groovy
// settings.gradle
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven {
            name = '3arthMaven'
            url = 'https://3arthqu4ke.github.io/maven'
        }
    }
}
```

```groovy
// build.gradle
plugins {
    id 'io.github.headlesshq.headlessmc-gradle-plugin' version '1.0.0'
}

repositories {
    // HeadlessMc itself comes from Maven Central
    mavenCentral()
}

tasks.register('runHeadlessMc', HeadlessMcRunTask) {
    // works like a Copy task: copy your release jar into the mods folder of the game
    from(remapJar)
    // Fabric API too, if you need it
    from(configurations.modRuntimeClasspath) {
        include("fabric-api-${fabric_version}.jar")
    }
    into(modsDirectory)

    minecraftVersion = '1.21.4'
    platform = HmcPlatform.FABRIC   // or 'fabric'
    loaderVersion = '0.16.14'       // optional, latest by default
}
```

```shell
./gradlew headlessmc --args="login"   # once, the account is stored in run/headlessmc
./gradlew runHeadlessMc
```

For CI, run headless with an offline account and mc-runtime-test, which joins a world, runs the game tests and quits:

```groovy
tasks.register('runHeadlessMcTest', HeadlessMcRunTask) {
    from(remapJar)
    into(modsDirectory)
    modrinth 'fabric-api', 'mc-runtime-test'

    minecraftVersion = '1.21.4'
    platform = HmcPlatform.FABRIC
    headless = true
    offline = true
    dummyAssets = true
    ciOptions()  // pauseOnLostFocus:false, onboardAccessibility:false, narrator:0
}
```

See the [examples](examples) for Fabric Loom, NeoForge ModDevGradle, servers, version matrices, the Kotlin DSL,
HeadlessMc plugins and more.

## How HeadlessMc is run

HeadlessMc 3 runs on Java 25, which Gradle or your build might not. It is never loaded into Gradle,
every task starts it as a separate process with command line arguments:

| `headlessmc.distribution` | Runs                                                                                              |
|---------------------------|---------------------------------------------------------------------------------------------------|
| `AUTO` (default)          | `JVM` if a Java 25 toolchain is available, otherwise `NATIVE`                                     |
| `JVM`                     | `io.github.headlesshq:headlessmc-application:<version>:runner` (Maven Central) on a Java 25 toolchain |
| `NATIVE`                  | the native executable from the GitHub release (Linux, macOS, Windows; x64/arm64), verified with its SHA-256 hash |

A checkout of HeadlessMc, e.g. a git submodule, can be used as included build with `headlessmc.includedBuild`,
see [Writing HeadlessMc plugins](#writing-headlessmc-plugins).

Gradle finds Java 25 toolchains on your machine, the
[foojay resolver](https://github.com/gradle/foojay-toolchains) can download one.
The jar is resolved through the `headlessmcRuntime` configuration and `headlessmc.executable` can point to a native
executable, so a local HeadlessMc build can be used too, see [examples/local-headlessmc](examples/local-headlessmc).

## Project scoped

HeadlessMc normally uses `~/.minecraft`, `~/.config/headlessmc`, etc.
The plugin configures HeadlessMc (`hmc.files.location`, `hmc.files.mc`, `hmc.files.game`) to place everything
in the project instead, and fails the build if a directory is outside the build or if a task tries to set these
keys itself:

| Directory                     | Property                       | Contents                                                     |
|-------------------------------|--------------------------------|--------------------------------------------------------------|
| `run/headlessmc`              | `headlessmc.directory`         | everything below                                             |
| `run/headlessmc/launcher`     | `headlessmc.launcherDirectory` | config, logs, accounts, Java runtimes, plugins, servers, cache |
| `run/headlessmc/mc`           | `headlessmc.mcDirectory`       | versions, libraries, assets                                  |
| `run/headlessmc/game/<task>`  | `gameDirectory` of the task    | the game directory of a client run task                      |
| `run/headlessmc/native`       | `headlessmc.nativeDirectory`   | downloaded native executables                                |

Directories may be anywhere inside the root project, e.g. to share one HeadlessMc between subprojects.
Add `run/` to your `.gitignore`, it contains your login.

## `headlessmc` extension

```groovy
headlessmc {
    version = '3.0.0-RC8'
    group = 'io.github.headlesshq'        // Maven group of HeadlessMc, e.g. for forks
    // includedBuild = 'headlessmc'       // run an included build of HeadlessMc instead
    distribution = HmcDistribution.AUTO
    javaVersion = 25                     // or javaLauncher = javaToolchains.launcherFor { ... }
    jvmArgs = ['-Xmx512m']               // for HeadlessMc, not the game
    directory = layout.projectDirectory.dir('run/headlessmc')
    launcherConfig.put('hmc.log.console-level', 'INFO')  // any HeadlessMc config, except hmc.files.*
    jline = false                        // JLine does not work well inside Gradle
    managePlugins = true                 // sync the headlessmcPlugins configuration into the plugins directory
    // executable = file('...')          // a native executable to use
    // nativeSha256 = '...'              // expected hash, taken from the GitHub release otherwise
    // nativeDownloadUrl = '...'         // a mirror of the GitHub releases
}

dependencies {
    headlessmcPlugins files('quilt-plugin.jar')  // HeadlessMc plugins to install
    // headlessmcRuntime files('headlessmc.jar') // replaces the HeadlessMc jar
}
```

## Tasks

`./gradlew headlessmc --args="<command>"` runs any HeadlessMc command, e.g. `login`, `version list`,
`mod search sodium`, `config ls`. `HeadlessMcExec` tasks do the same with fixed `args`.

`HeadlessMcRunTask` launches the game. Before launching, it copies everything configured with `from(...)`
`into(...)` the destination, by default the `modsDirectory`, after emptying it (`cleanDestination = false` to keep
old files). `include`, `exclude`, `rename`, `eachFile`, `from(...) { ... }`, `into('dir') { ... }` and
`copySpec { ... }` work like for a `Copy` task.

| Property                       | HeadlessMc                                          |
|--------------------------------|-----------------------------------------------------|
| `minecraftVersion`             | `launch <version>`                                  |
| `platform`, `loaderVersion`    | `launch <platform> <version> <build>`, builds are named like in `version list --remote <platform> <version>`, e.g. `0.16.14` for Fabric, `265` for NeoForge 21.1.265 |
| `server`, `acceptEula`         | `launch server ...`, `--eula-accept`                |
| `headless`                     | `--headless`                                        |
| `offline`                      | `--offline`                                         |
| `dummyAssets`                  | `hmc.assets.dummy=true`                             |
| `gameJvmArgs`, `gameArgs`      | `--jvm`, `--game`                                   |
| `joinServer`, `resolution`, `retries`, `patchers` | `--server`, `--resolution`, `--retries`, `--patchers` |
| `extraLaunchArgs`              | more arguments for `launch`                         |
| `launchArgs`                   | replaces the launch command                         |
| `modrinth`                     | `mod add mod <id> <version>` before launching       |
| `commands`                     | HeadlessMc commands run before launching            |
| `minecraftOptions`, `ciOptions()` | merged into `options.txt`                        |
| `gameJavaLaunchers`            | Java installations for the game (`hmc.java.versions`) |
| `launcherConfig`               | HeadlessMc configuration for this task              |
| `gameDirectory`                | `hmc.files.game`                                    |

`runDirectory` is the directory the game runs in (`gameDirectory` for clients, HeadlessMc's server directory for
servers), `modsDirectory` and `serverPluginsDirectory` are inside it.

## Writing HeadlessMc plugins

```groovy
plugins {
    id 'io.github.headlesshq.hmc-plugin-gradle-plugin' version '1.0.0'
}

repositories {
    mavenCentral()
}

headlessmcPlugin {
    id = 'example'
    name = 'Example Plugin'
    description = 'Adds the hello command.'
    authors = ['You']
    license = 'MIT'
    // dependency 'other-plugin', '[1.0,2)'
    // mutableJar = file('...')   // headlessmc-mutable.zip of a local HeadlessMc build
}

dependencies {
    include 'com.github.lalyos:jfiglet:0.0.9'   // nested into the plugin jar (Jar-in-Jar)
}
```

It compiles the plugin against HeadlessMc (`headlessmc` configuration, never packaged) on Java 25,
nests the `include` libraries, generates `META-INF/headlessmc-plugin.json`, and applies the HeadlessMc plugin:
every HeadlessMc task runs the project's HeadlessMc with the freshly built plugin installed.

```shell
./gradlew headlessmc --args="hello --banner"
```

Plugins need HeadlessMc 3.0.0-RC9, which is not released yet. Until then, plugins are built against
`io.github.headlesshq:headlessmc-application:3.0.0-RC9-SNAPSHOT` and are compatible with `[3.0.0-RC9,4)`.
The [plugin template](https://github.com/headlesshq/headlessmc-plugin-template) includes the `feat/plugins` branch
of HeadlessMc and this repository as git submodules:

```groovy
// settings.gradle
pluginManagement {
    includeBuild('deps/headlessmc-gradle-plugin')
}

// the included HeadlessMc builds its mutable jar, needed to load plugins, and reports the version it will be released as
gradle.startParameter.projectProperties += [mutableJar: 'true', applicationVersion: '3.0.0-RC9-SNAPSHOT']
includeBuild('deps/headlessmc')
```

```groovy
// build.gradle
headlessmc {
    // compiles against, builds and runs the included HeadlessMc
    includedBuild = 'headlessmc'
}
```

Alternatively, publish HeadlessMc with `./gradlew publishToMavenLocal -Pversion=3.0.0-RC9-SNAPSHOT` and add
`mavenLocal()`, see [examples/hmc-plugin](examples/hmc-plugin).
Plugins can be published to the [HeadlessMc plugin index](https://github.com/headlesshq/headlessmc-plugins).

## Using a JitPack build

Commits of this repository can be used through [JitPack](https://jitpack.io) before they are released:

```groovy
// settings.gradle
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven { url = 'https://jitpack.io' }
    }
    resolutionStrategy {
        eachPlugin {
            // JitPack does not publish the plugin markers, so map the plugin ids to the JitPack modules
            if (requested.id.namespace == 'io.github.headlesshq') {
                useModule("com.github.headlesshq.headlessmc-gradle-plugin:${requested.id.name}:${requested.version}")
            }
        }
    }
}
```

```groovy
// build.gradle
plugins {
    id 'io.github.headlesshq.hmc-plugin-gradle-plugin' version '<commit>'
}
```

JitPack builds of HeadlessMc commits work too: `headlessmc { group = 'com.github.headlesshq.headlessmc'; version = '<commit>' }`
with `maven { url = 'https://jitpack.io' }` in the repositories. They always run on the JVM.

## Development

```shell
./gradlew build            # unit tests and functional tests (TestKit, against a fake HeadlessMc)
./gradlew integrationTest  # runs the real HeadlessMc, needs network access
HMC_IT_LAUNCH=true ./gradlew integrationTest  # also launches Minecraft headless with mc-runtime-test
```
