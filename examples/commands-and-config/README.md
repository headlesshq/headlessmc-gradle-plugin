# Commands and configuration

* `./gradlew headlessmc --args="..."` runs any HeadlessMc command with the project's HeadlessMc.
  Without `--args` the HeadlessMc shell starts (`./gradlew headlessmc --console=plain` works best).
* `HeadlessMcExec` tasks run commands you need often, e.g. `downloadVersions`.
* `headlessmc.launcherConfig` and the task's `launcherConfig` set HeadlessMc configuration,
  the `hmc.files.*` keys are managed by the plugin, so HeadlessMc cannot leave the project.
* `HeadlessMcRunTask` runs `commands` and `modrinth` before launching, `minecraftOptions` are written into
  `options.txt` and `launchArgs` replaces the launch command completely.
