plugins {
    id("io.github.headlesshq.hmc-plugin-gradle-plugin")
}

headlessmcPlugin {
    id = "greeter"
    name = "Greeter"
    description = "Adds the greet command."
    authors.add("You")
}
