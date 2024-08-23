import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI

plugins {
    kotlin("jvm") version "1.8.21"
    id("org.jetbrains.dokka") version "1.9.20"
    `java-gradle-plugin`
    `maven-publish`
}

group = "io.github.headlesshq"
version = "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven {
        name = "3arthMaven"
        url = URI("https://3arthqu4ke.github.io/maven")
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("me.earth.headlessmc:headlessmc-launcher:2.1.0-SNAPSHOT")
    implementation("me.earth.headlessmc:headlessmc-jline:2.1.0-SNAPSHOT")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    if (JavaVersion.current().isJava9Compatible) {
        options.release.set(8)
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

val dokkaJavadocJar: TaskProvider<Jar> by tasks.registering(Jar::class) {
    dependsOn(tasks.getByName("dokkaJavadoc"))
    archiveClassifier.set("javadoc")
    from(tasks.getByName("dokkaJavadoc").property("outputDirectory"))
}

gradlePlugin {
    plugins {
        create("simplePlugin") {
            id = "io.github.headlesshq.headlessmc-gradle-plugin"
            implementationClass = "io.github.headlesshq.headlessmc.gradle.HeadlessMcPlugin"
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "${project.group}"
            artifactId = project.base.archivesName.get()
            version = "${project.version}"
            from(components.getByName("java"))
            artifact(dokkaJavadocJar)
        }
    }

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
