import org.gradle.jvm.tasks.Jar

plugins {
    kotlin("jvm") version "1.3.72"
    id("org.jmailen.kotlinter") version "2.3.2"
    id("org.jetbrains.dokka") version "0.10.1"
    signing
    `maven-publish`
    id("io.codearte.nexus-staging") version "0.21.2"
}

group = "dev.fuelyour"
version = "0.0.6"
val projectDescription = "Core libraries used by microservices created from " +
    "the vertx-kuickstart template"
description = projectDescription
extra["isReleaseVersion"] = !version.toString().endsWith("SNAPSHOT")

buildscript {
    repositories {
        jcenter()
        mavenCentral()
    }
}

repositories {
    jcenter()
    mavenCentral()
}

val vertxVersion = "3.8.2"

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))

    implementation("dev.fuelyour:named-to-positional-sql-params:0.0.6")
    implementation("io.vertx:vertx-core:$vertxVersion")
    implementation("io.vertx:vertx-web-api-contract:$vertxVersion")
    implementation("io.vertx:vertx-config:$vertxVersion")
    implementation("io.vertx:vertx-auth-jwt:$vertxVersion")
    implementation("io.vertx:vertx-pg-client:$vertxVersion")
    implementation("io.vertx:vertx-lang-kotlin:$vertxVersion")
    implementation("io.vertx:vertx-lang-kotlin-coroutines:$vertxVersion")
    implementation("org.flywaydb:flyway-core:6.0.0")
    implementation("org.reflections:reflections:0.9.11")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.13.1")
    implementation("org.apache.commons:commons-collections4:4.0")

    testImplementation("io.mockk:mockk:1.9.3")
    testImplementation("io.kotest:kotest-runner-junit5:4.0.5")
    testImplementation("io.kotest:kotest-assertions-core-jvm:4.0.5")
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = true
        }
    }
    dokka {
        outputFormat = "html"
        outputDirectory = "$buildDir/dokka"
    }
}

val sourcesJar by tasks.creating(Jar::class) {
    dependsOn(JavaPlugin.CLASSES_TASK_NAME)
    archiveClassifier.set("sources")
    from(sourceSets["main"].allSource)
}

val dokkaJar by tasks.creating(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    description = "Assembles Kotlin docs with Dokka"
    archiveClassifier.set("javadoc")
    from(tasks.dokka)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifact(sourcesJar)
            artifact(dokkaJar)
            pom {
                name.set("${project.group}:${rootProject.name}")
                description.set(projectDescription)
                url.set("https://github.com/fuelyourdev/${rootProject.name}")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set(
                            "http://www.opensource.org/licenses/mit-license.php"
                        )
                    }
                }
                developers {
                    developer {
                        id.set("fuelyourdev")
                        name.set("Trevor Young")
                        email.set("trevor@fuelyour.dev")
                        organization.set("Fuel Your Dev, LLC")
                        organizationUrl.set("https://fuelyour.dev")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/fuelyourdev/${rootProject.name}.git")
                    developerConnection.set(
                        "scm:git:git://github.com/fuelyourdev/${rootProject.name}.git"
                    )
                    url.set("https://github.com/fuelyourdev/${rootProject.name}/tree/master")
                }
            }
        }
    }
    repositories {
        maven {
            val repoUrl = if (rootProject.extra["isReleaseVersion"] as Boolean) {
                "https://oss.sonatype.org/service/local/staging/deploy/maven2"
            } else {
                "https://oss.sonatype.org/content/repositories/snapshots"
            }
            url = uri(repoUrl)
            credentials {
                username = System.getenv("ossrhUsername")
                password = System.getenv("ossrhPassword")
            }
        }
    }
}

if (rootProject.extra["isReleaseVersion"] as Boolean) {
    signing {
        val signingKey: String? by project
        val signingPassword: String? by project
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["mavenJava"])
    }
}

nexusStaging {
    username = System.getenv("ossrhUsername")
    password = System.getenv("ossrhPassword")
    numberOfRetries = 60
    delayBetweenRetriesInMillis = 5000
}
