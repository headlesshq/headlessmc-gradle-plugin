package io.github.headlesshq.headlessmc.gradle

import io.github.headlesshq.headlessmc.gradle.internal.Args
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

/**
 * Runs HeadlessMc with the given arguments, e.g.
 * `./gradlew headlessmc --args="login"` or `./gradlew headlessmc --args="version list"`.
 */
@DisableCachingByDefault(because = "Runs HeadlessMc, which has side effects outside of Gradle's knowledge")
abstract class HeadlessMcExec : AbstractHeadlessMcTask() {
    /**
     * The arguments for HeadlessMc. Without arguments HeadlessMc starts its interactive shell.
     */
    @get:Input
    abstract val args: ListProperty<String>

    /**
     * Sets the arguments from a command line string, e.g. `--args="launch --offline fabric 1.21.4"`.
     *
     * @param commandLine the arguments, quotes group arguments.
     */
    @Option(option = "args", description = "The arguments for HeadlessMc, e.g. --args=\"launch fabric 1.21.4\".")
    fun setArgsString(commandLine: String) {
        args.set(Args.split(commandLine))
    }

    /**
     * Adds arguments.
     *
     * @param args the arguments to add.
     */
    fun args(vararg args: Any) {
        args.forEach { this.args.add(it.toString()) }
    }

    @TaskAction
    fun exec() {
        prepareAndRun(args.get())
    }
}
