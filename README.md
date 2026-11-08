<!--suppress HtmlDeprecatedAttribute -->
<h1 align="center" style="font-weight: normal;"><b>HeadlessMc Gradle Plugin</b></h1>
<p align="center">Gradle plugins for working with and for HeadlessMc.</p>
<p align="center"><img src="docs/logo.png" alt="logo" style="width:250px;"></p>
<p align="center"> <a href="https://github.com/headlesshq/headlessmc">HeadlessMc</a>  | <a href="https://github.com/headlesshq/hmc-specifics">HMC-Specifics</a> | <a href="https://github.com/headlesshq/hmc-optimizations">HMC-Optimizations</a></p>


Gradle plugins for [HeadlessMc](https://github.com/headlesshq/headlessmc), the command line launcher for Minecraft.

* **`headlessmc-gradle-plugin`** launches Minecraft with HeadlessMc.
  When developing for a modloader, such as fabric, neoforge or forge,
  their Gradle plugins usually supply a task to run Minecraft, such as `runClient`. 
  Launching the game with these tasks works very differently from how players launch the game.
  Problems with the packaging of your release jar, mixins or mappings only show up with a "real" launcher,
  and you are also running the game with an offline account.
  With this plugin you run your release jar against a real launcher and even in your CI/CD pipeline, 
  headless, together with [mc-runtime-test](https://github.com/headlesshq/mc-runtime-test).
* **`headlessmc-dev-plugin`** helps you build plugins for the HeadlessMc launcher.

## Setup

```groovy
plugins {
    id 'io.github.headlesshq.headlessmc-gradle-plugin' version '1.0.0'
}

tasks.register('runHeadlessMc', HeadlessMcRunTask) {
    from(remapJar)
    from(configurations.modRuntimeClasspath) {
        include("fabric-api-${fabric_version}.jar")
    }
    into(modsDirectory)

    minecraftVersion = '1.21.4'
    platform = HmcPlatform.FABRIC
    loaderVersion = '0.16.14'
}
```

```shell
./gradlew headlessmc --args="login"
./gradlew runHeadlessMc
```

This gives you a test task:
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
    ciOptions()
}
```

See the [examples](examples) for Fabric Loom, NeoForge ModDevGradle, servers, version matrices, the Kotlin DSL,
HeadlessMc plugins and more.

## Writing HeadlessMc plugins
Use our [headlessmc-plugin-template](https://github.com/headlesshq/headlessmc-plugin-template).

```groovy
plugins {
    id 'io.github.headlesshq.headlessmc-dev-plugin' version '1.0.0'
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

## Releasing

Releases are managed by [release-please](https://github.com/googleapis/release-please). Commits to `main` must follow
[Conventional Commits](https://www.conventionalcommits.org/) (e.g. `feat: ...`, `fix: ...`), since they determine the
next version and the `CHANGELOG.md`. To release, merge the release-please PR: this updates the version in
`gradle.properties`, creates the GitHub release and publishes the plugins to the Gradle Plugin Portal.
If publishing fails, re-run it with the `Publish` workflow and the version to publish.
