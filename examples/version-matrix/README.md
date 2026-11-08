# Version matrix

Registers a `HeadlessMcRunTask` for every Minecraft version and platform in a loop,
e.g. to check that a multi-loader mod starts everywhere.

```shell
./gradlew tasks --group headlessmc
./gradlew runFabric1_21_4
./gradlew runAll
```

All tasks share `run/headlessmc/mc`, so a version is only downloaded once, but every task has its own
game directory. In CI, a matrix job could run one task each: `./gradlew ${{ matrix.task }}`.
