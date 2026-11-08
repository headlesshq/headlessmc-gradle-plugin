package io.github.headlesshq.headlessmc.gradle;

import io.github.headlesshq.headlessmc.gradle.internal.Args;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.work.DisableCachingByDefault;

import java.util.List;

/**
 * Runs HeadlessMc with the given arguments, e.g.
 * {@code ./gradlew headlessmc --args="login"} or {@code ./gradlew headlessmc --args="version list"}.
 */
@DisableCachingByDefault(because = "Runs HeadlessMc, which has side effects outside of Gradle's knowledge")
public abstract class HeadlessMcExec extends AbstractHeadlessMcTask {
    /**
     * @return the arguments for HeadlessMc. Without arguments HeadlessMc starts its interactive shell.
     */
    @Input
    public abstract ListProperty<String> getArgs();

    /**
     * Sets the arguments from a command line string, e.g. {@code --args="launch --offline fabric 1.21.4"}.
     *
     * @param commandLine the arguments, quotes group arguments.
     */
    @Option(option = "args", description = "The arguments for HeadlessMc, e.g. --args=\"launch fabric 1.21.4\".")
    public void setArgsString(String commandLine) {
        getArgs().set(Args.split(commandLine));
    }

    /**
     * Adds arguments.
     *
     * @param args the arguments to add.
     */
    public void args(Object... args) {
        for (Object arg : args) {
            getArgs().add(String.valueOf(arg));
        }
    }

    @TaskAction
    public void exec() {
        List<String> args = getArgs().get();
        prepareAndRun(args);
    }

}
