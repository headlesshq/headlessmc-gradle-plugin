description = "Runs Minecraft with the HeadlessMc launcher, scoped to the Gradle project."

val functionalTest: SourceSet = sourceSets.create("functionalTest")
val integrationTest: SourceSet = sourceSets.create("integrationTest")
// A tiny stand-in for HeadlessMc, which records how it was started. Used by the functional tests.
val fakeHeadlessMc: SourceSet = sourceSets.create("fakeHeadlessMc")

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(gradleApi())

    "functionalTestImplementation"(platform("org.junit:junit-bom:5.13.4"))
    "functionalTestImplementation"("org.junit.jupiter:junit-jupiter")
    "functionalTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    "functionalTestImplementation"(gradleTestKit())
    "functionalTestImplementation"(sourceSets.main.get().output)

    "integrationTestImplementation"(platform("org.junit:junit-bom:5.13.4"))
    "integrationTestImplementation"("org.junit.jupiter:junit-jupiter")
    "integrationTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    "integrationTestImplementation"(gradleTestKit())
}

val headlessMcVersion = providers.gradleProperty("headlessmc_version")
val generateVersionProperties = tasks.register("generateVersionProperties") {
    val output = layout.buildDirectory.dir("generated/resources/version")
    val pluginVersion = project.version.toString()
    inputs.property("version", pluginVersion)
    inputs.property("headlessmcVersion", headlessMcVersion)
    outputs.dir(output)
    doLast {
        val file = output.get().file("io/github/headlesshq/headlessmc/gradle/version.properties").asFile
        file.parentFile.mkdirs()
        file.writeText("version=$pluginVersion\nheadlessmc=${headlessMcVersion.get()}\n")
    }
}

sourceSets.main { resources.srcDir(generateVersionProperties) }

val fakeHeadlessMcJar = tasks.register<Jar>("fakeHeadlessMcJar") {
    archiveFileName.set("fake-headlessmc.jar")
    destinationDirectory.set(layout.buildDirectory.dir("fake"))
    from(fakeHeadlessMc.output)
    manifest { attributes("Main-Class" to "io.github.headlesshq.headlessmc.fake.FakeHeadlessMc") }
}

tasks.named<JavaCompile>(fakeHeadlessMc.compileJavaTaskName) {
    options.release.set(17)
}

gradlePlugin {
    website.set("https://github.com/headlesshq/headlessmc-gradle-plugin")
    vcsUrl.set("https://github.com/headlesshq/headlessmc-gradle-plugin")
    testSourceSets(functionalTest, integrationTest)
    plugins {
        create("headlessmc") {
            id = "io.github.headlesshq.headlessmc"
            implementationClass = "io.github.headlesshq.headlessmc.gradle.HeadlessMcPlugin"
            displayName = "HeadlessMc"
            description = project.description
            tags.addAll("headlessmc", "minecraft", "launcher", "testing")
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

val functionalTestTask = tasks.register<Test>("functionalTest") {
    description = "Runs the TestKit tests against a fake HeadlessMc."
    group = "verification"
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
    useJUnitPlatform()
    dependsOn(fakeHeadlessMcJar)
    val fakeJar = fakeHeadlessMcJar.flatMap { it.archiveFile }
    inputs.file(fakeJar)
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("-Dfake.headlessmc.jar=${fakeJar.get().asFile.absolutePath}")
    })
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
}

tasks.register<Test>("integrationTest") {
    description = "Runs the real HeadlessMc, needs network access and a Java 25 toolchain."
    group = "verification"
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(functionalTestTask)
    // talks to the network, never cache or skip it
    outputs.cacheIf { false }
    outputs.upToDateWhen { false }
    systemProperty("headlessmc.version", headlessMcVersion.get())
}

tasks.check { dependsOn(functionalTestTask) }
