# Fabric mod with Loom

The example mod from the Fabric template, with two HeadlessMc run tasks:

* `runHeadlessMc` copies the remapped release jar and Fabric API into the mods folder and launches the game with a
  window. Log in once with `./gradlew headlessmc --args="login"`, the account is stored in `run/headlessmc/launcher`.
* `runHeadlessMcTest` is meant for CI: headless, offline, dummy assets, with
  [mc-runtime-test](https://github.com/headlesshq/mc-runtime-test) from Modrinth, which joins a world, runs the
  game tests and quits. See [.github/workflows/run-headlessmc.yml](.github/workflows/run-headlessmc.yml).

```shell
./gradlew runHeadlessMcTest
```

Unlike Loom's `runClient`, these tasks run the jar you would release, through a real launcher, so problems with
the packaging, refmaps, mixins or mappings show up.
