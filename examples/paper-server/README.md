# Servers

`server = true` launches a server instead of the client. HeadlessMc places each server in
`run/headlessmc/launcher/server/<name>`; the task exposes it as `runDirectory`, with `modsDirectory`
and `serverPluginsDirectory` inside it.

```shell
./gradlew runPaper
./gradlew runFabricServer
```

Type `stop` to stop the server, the console is forwarded to HeadlessMc.
