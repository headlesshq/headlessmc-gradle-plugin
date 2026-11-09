import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.net.URI

plugins {
    kotlin("jvm") version "2.4.21" apply false
    id("org.jetbrains.dokka") version "2.3.0" apply false
    id("org.jetbrains.dokka-javadoc") version "2.3.0" apply false
    // publishes the plugins to the Gradle Plugin Portal: ./gradlew publishPlugins, see .github/workflows/lifecycle.yml and publish.yml
    id("com.gradle.plugin-publish") version "2.0.0" apply false
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "java-gradle-plugin")
    apply(plugin = "maven-publish")
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "org.jetbrains.dokka-javadoc")
    apply(plugin = "com.gradle.plugin-publish")

    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
    }

    dependencies {
        // Gradle provides the Kotlin standard library at runtime, see kotlin.stdlib.default.dependency
        "compileOnly"(kotlin("stdlib"))
        "testImplementation"(kotlin("stdlib"))
    }

    extensions.configure<JavaPluginExtension> {
        withSourcesJar()
        withJavadocJar()
    }

    // the javadoc jar contains the Dokka documentation in the Javadoc format
    tasks.named<Jar>("javadocJar") {
        from(tasks.named("dokkaGeneratePublicationJavadoc"))
    }

    // Gradle 8 and 9 run on Java 17+, HeadlessMc itself is never loaded into Gradle,
    // it is always started as a separate process.
    tasks.withType<JavaCompile>().configureEach {
        options.release.set(17)
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-serial", "-Xlint:-processing"))
    }

    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            // the plugins run with the Kotlin of Gradle, 2.0 since Gradle 8.11, so they work with Gradle 8.11+
            apiVersion.set(KotlinVersion.KOTLIN_2_0)
            languageVersion.set(KotlinVersion.KOTLIN_2_0)
            // 2.0 is deprecated, but newer versions need Gradle 9
            freeCompilerArgs.addAll("-Xjdk-release=17", "-Xsuppress-version-warnings")
            allWarningsAsErrors.set(true)
        }
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
