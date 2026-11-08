import java.net.URI

plugins {
    // publishes the plugins to the Gradle Plugin Portal: ./gradlew publishPlugins, see .github/workflows/release.yml
    id("com.gradle.plugin-publish") version "2.0.0" apply false
}

subprojects {
    apply(plugin = "java-gradle-plugin")
    apply(plugin = "maven-publish")
    apply(plugin = "com.gradle.plugin-publish")

    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
    }

    dependencies {
        "compileOnly"("org.jspecify:jspecify:1.0.0")
    }

    extensions.configure<JavaPluginExtension> {
        withSourcesJar()
        withJavadocJar()
    }

    // Gradle 8 and 9 run on Java 17+, HeadlessMc itself is never loaded into Gradle,
    // it is always started as a separate process.
    tasks.withType<JavaCompile>().configureEach {
        options.release.set(17)
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-serial", "-Xlint:-processing"))
    }

    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
    }

    tasks.withType<ValidatePlugins>().configureEach {
        enableStricterValidation.set(true)
        failOnWarning.set(true)
    }

    extensions.configure<PublishingExtension> {
        repositories {
            if (System.getenv("DEPLOY_TO_GITHUB_PACKAGES_URL") != null) {
                maven {
                    name = "GithubPagesMaven"
                    url = URI(System.getenv("DEPLOY_TO_GITHUB_PACKAGES_URL"))
                    credentials {
                        username = System.getenv("GITHUB_USER")
                        password = System.getenv("GITHUB_TOKEN")
                    }
                }
            } else {
                maven {
                    name = "BuildDirMaven"
                    url = rootProject.layout.buildDirectory.dir("maven").get().asFile.toURI()
                }
            }
        }
    }
}
