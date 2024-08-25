# HeadlessMc-Gradle-Plugin

Adds a gradle task for running Minecraft with the [HeadlessMc](https://github.com/3arthqu4ke/headlessmc) launcher.
Similarly to mc-runtime-test the idea is that your usual gradle run task
works very differently from how a user will actually launch the game.
Many problems like the packaging of your release jar, mixin-issues
or mapping issues can only be detected by launching the game with a "real" launcher.
With this task you can use your release jar to run the game.

The project is still in early development.
To use add the repository for the plugin inside your `settings.gradle`:

```groovy
pluginManagement {
    repositories {
	// ...
	mavenCentral()
	gradlePluginPortal()
	maven {
	    name = "3arthMaven"
	    url = "https://3arthqu4ke.github.io/maven"
	}
    }
}
```

Then add the plugin and a `HeadlessMcRunTask` to your `build.gradle`:

```groovy
plugins {
    id 'io.github.headlesshq.headlessmc-gradle-plugin' version '0.3.0'
}

tasks.register('runWithHeadlessMc', HeadlessMcRunTask) {
    // copy your release jar to the modsDirectory
    from(remapJar)
    // Fabric API too, if you need it
    from(configurations.modImplementation) {
	include("*fabric-api-${fabric_version}.jar")
    }
    into(modsDirectory)

    version = '1.21'
    modlauncher = Modlauncher.FABRIC
    modLoaderVersion = '0.15.11' // optional
    // other options
}
```
