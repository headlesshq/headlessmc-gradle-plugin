# Multi-project: a HeadlessMc plugin and a mod

* `:platform-plugin` is a HeadlessMc plugin (`io.github.headlesshq.headlessmc-dev-plugin`), it adds `greet`.
* `:mod` installs it with `headlessmcPlugins(project(":platform-plugin"))` and runs HeadlessMc with it.
  This is how a mod could use a HeadlessMc plugin, e.g. one adding a platform like Quilt.

Both share `run/headlessmc` in the root project.

```shell
./gradlew :mod:greet
./gradlew :mod:runClient
```

Like the `hmc-plugin` example, this runs HeadlessMc 3.0.0-RC9 from Maven Central by default.
To use a local HeadlessMc build instead, see its README:

```shell
./gradlew :mod:greet \
  -PheadlessmcJar=<headlessmc>/headlessmc-application/build/quarkus-app/quarkus-run.jar \
  -PheadlessmcMutableJar=<headlessmc>/headlessmc-application/build/headlessmc-mutable.zip
```
