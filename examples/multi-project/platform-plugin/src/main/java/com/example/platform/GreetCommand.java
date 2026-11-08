package com.example.platform;

import io.github.headlesshq.headlessmc.console.Console;
import io.github.headlesshq.headlessmc.plugins.HeadlessMcSubcommand;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import picocli.CommandLine;

/**
 * Adds {@code headlessmc greet}.
 */
@Dependent
@CommandLine.Command(name = "greet", mixinStandardHelpOptions = true, description = "Greets the mod developer.")
public class GreetCommand implements Runnable, HeadlessMcSubcommand {
    private final Console console;

    @Inject
    public GreetCommand(Console console) {
        this.console = console;
    }

    @Override
    public void run() {
        console.write("Hello from the greeter plugin, built in the same Gradle build as the mod!");
    }

}
