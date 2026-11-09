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
    id 'io.github.headlesshq.headlessmc' version '1.0.0' // x-release-please-version
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
    id 'io.github.headlesshq.headlessmc-dev-plugin' version '1.0.0' // x-release-please-version
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

> [!NOTE]
> Until HeadlessMc 3.0.0-RC9 is on Maven Central, the dev plugin's default HeadlessMc version is
> `3.0.0-RC9-SNAPSHOT`, which can't be resolved from any public repository. Set `headlessmc.includedBuild`
> (a local HeadlessMc checkout) or `mutableJar` (see above) until a release switches the default to RC9.

### Publishing to the plugin index
`plugin search` and `plugin download` read the `index.json` of
[headlessmc-plugins](https://github.com/headlesshq/headlessmc-plugins). `./gradlew generatePluginIndexEntry` builds
the plugin jar and writes `build/headlessmc-plugin/index-entry.json`, the entry for it, with the sha256 and size of
the jar and the same id, name, description, authors, license, homepage, `headlessmc` range and `java` version as
`META-INF/headlessmc-plugin.json`:

```json
{
  "id": "example",
  "name": "Example Plugin",
  "description": "Adds the hello command.",
  "authors": ["You"],
  "license": "MIT",
  "homepage": "https://github.com/you/example-plugin",
  "downloads": [
    {
      "version": "1.0.0",
      "headlessmc": "[3.0.0-RC9,4)",
      "java": 25,
      "url": "https://github.com/you/example-plugin/releases/download/1.0.0/example-plugin-1.0.0.jar",
      "sha256": "…",
      "size": 12345
    }
  ]
}
```

```groovy
headlessmcPlugin {
    homepage = 'https://github.com/you/example-plugin'
    // a github.com homepage defaults to <homepage>/releases/download/<version>/<jar file name>,
    // otherwise the download url has to be set
    // downloadUrl = 'https://example.com/example-plugin-1.0.0.jar'
    // icon = 'https://example.com/icon.png'   // optional
}
```

Upload exactly that jar, e.g. to the GitHub release of the version, then open a pull request on headlessmc-plugins:
if the plugin is not in its `index.json` yet, add the entry to `plugins`, otherwise append the release in
`downloads` of the entry to the `downloads` of the existing entry (and update its other fields if they changed).
