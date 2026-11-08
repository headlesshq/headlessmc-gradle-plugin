# Vanilla client (Kotlin DSL)

The smallest setup: launch vanilla Minecraft with HeadlessMc.

```shell
./gradlew runVanilla          # headless, offline account, dummy assets
./gradlew headlessmc --args="login"
./gradlew runVanillaWindowed  # with a window and your account
```

Everything HeadlessMc writes lives in `run/headlessmc` inside this project:

| Directory                  | Contents                                                    |
|----------------------------|-------------------------------------------------------------|
| `run/headlessmc/launcher`  | HeadlessMc config, logs, accounts, downloaded Java, plugins |
| `run/headlessmc/mc`        | versions, libraries, assets                                 |
| `run/headlessmc/game/<task>` | the game directory of each run task (mods, saves, ...)    |
| `run/headlessmc/native`    | the native HeadlessMc executable, if no Java 25 is found    |
