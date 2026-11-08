description = "Builds HeadlessMc plugins: nests libraries into the plugin jar (Jar-in-Jar), generates the plugin " +
    "manifest and runs the plugin with a HeadlessMc scoped to the project."

val functionalTest: SourceSet = sourceSets.create("functionalTest")

val generateDefaults = tasks.register("generateHeadlessMcDefaults") {
    val properties = mapOf(
        "group" to providers.gradleProperty("dev_plugin_headlessmc_group"),
        "version" to providers.gradleProperty("dev_plugin_headlessmc_version"),
        "compatible" to providers.gradleProperty("dev_plugin_headlessmc_compatible"),
    )
    properties.forEach { (key, value) -> inputs.property(key, value) }
    val output = layout.buildDirectory.dir("generated/resources/defaults")
    outputs.dir(output)
    doLast {
        val file = output.get().file("io/github/headlesshq/headlessmc/gradle/dev/headlessmc.properties").asFile
        file.parentFile.mkdirs()
        file.writeText(properties.entries.joinToString("") { (key, value) -> "$key=${value.get()}\n" })
    }
}

sourceSets.main { resources.srcDir(generateDefaults) }

dependencies {
    implementation(project(":headlessmc-gradle-plugin"))

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(gradleApi())

    "functionalTestImplementation"(platform("org.junit:junit-bom:5.13.4"))
    "functionalTestImplementation"("org.junit.jupiter:junit-jupiter")
    "functionalTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    "functionalTestImplementation"(gradleTestKit())
    "functionalTestImplementation"(sourceSets.main.get().output)
    "functionalTestImplementation"(project(":headlessmc-gradle-plugin"))
    "functionalTestImplementation"("com.fasterxml.jackson.core:jackson-databind:2.20.0")
}

gradlePlugin {
    website.set("https://github.com/headlesshq/headlessmc-gradle-plugin")
    vcsUrl.set("https://github.com/headlesshq/headlessmc-gradle-plugin")
    testSourceSets(functionalTest)
    plugins {
        create("headlessmcDevPlugin") {
            id = "io.github.headlesshq.headlessmc-dev-plugin"
            implementationClass = "io.github.headlesshq.headlessmc.gradle.dev.HeadlessMcDevPlugin"
            displayName = "HeadlessMc Dev"
            description = project.description
            tags.addAll("headlessmc", "minecraft", "plugin", "jar-in-jar")
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

evaluationDependsOn(":headlessmc-gradle-plugin")
val fakeHeadlessMcJar = project(":headlessmc-gradle-plugin").tasks.named<Jar>("fakeHeadlessMcJar")
val functionalTestTask = tasks.register<Test>("functionalTest") {
    description = "Runs the TestKit tests."
    group = "verification"
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
    useJUnitPlatform()
    val fakeJar = fakeHeadlessMcJar.flatMap { it.archiveFile }
    val examples = rootProject.layout.projectDirectory.dir("examples")
    inputs.file(fakeJar)
    inputs.files(fileTree(examples) { exclude("**/run/**", "**/build/**", "**/.gradle/**") })
        .withPathSensitivity(PathSensitivity.RELATIVE)
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "-Dfake.headlessmc.jar=${fakeJar.get().asFile.absolutePath}",
            "-Dexamples.dir=${examples.asFile.absolutePath}",
        )
    })
}

tasks.check { dependsOn(functionalTestTask) }
