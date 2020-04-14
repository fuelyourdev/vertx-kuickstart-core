import org.gradle.jvm.tasks.Jar

group = "dev.fuelyour"
version = "0.0.1-SNAPSHOT"
description = "Core libraries used by microservices created from the vertx-kuickstart template"
extra["isReleaseVersion"] = !version.toString().endsWith("SNAPSHOT")

plugins {
  kotlin("jvm") version "1.3.50"
  id("org.jetbrains.dokka") version "0.10.1"
  signing
  `maven-publish`
}

buildscript {
  repositories {
    mavenLocal()
    jcenter()
    mavenCentral()
  }
}

repositories {
  mavenLocal()
  jcenter()
  mavenCentral()
}

val vertxVersion = "3.8.1"

dependencies {
  implementation(kotlin("stdlib"))
  implementation(kotlin("reflect"))

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
  testImplementation("io.kotlintest:kotlintest-runner-junit5:3.3.2")
}

tasks {
  compileKotlin {
    kotlinOptions.jvmTarget = "11"
  }
  compileTestKotlin {
    kotlinOptions.jvmTarget = "11"
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
        description.set("Core libraries used by microservices created from the vertx-kuickstart template")
        url.set("https://github.com/fuelyourdev/vertx-kuickstart-core")
        licenses {
          license {
            name.set("MIT License")
            url.set("http://www.opensource.org/licenses/mit-license.php")
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
          connection.set("scm:git:git://github.com/fuelyourdev/vertx-kuickstart-core.git")
          developerConnection.set("scm:git:git://github.com/fuelyourdev/vertx-kuickstart-core.git")
          url.set("https://github.com/fuelyourdev/vertx-kuickstart-core/tree/master")
        }
      }
    }
  }
  repositories {
    maven {
      val publishUrl = if (rootProject.extra["isReleaseVersion"] as Boolean) {
        "https://oss.sonatype.org/service/local/staging/deploy/maven2"
      } else {
        "https://oss.sonatype.org/content/repositories/snapshots"
      }
      url = uri(publishUrl)
      credentials {
        username = extra["ossrhUsername"] as String
        password = extra["ossrhPassword"] as String
      }
    }
  }
}

if (rootProject.extra["isReleaseVersion"] as Boolean && gradle.taskGraph.hasTask("publish")) {
  signing {
    sign(publishing.publications["mavenJava"])
  }
}
