import org.gradle.jvm.tasks.Jar

plugins {
  kotlin("jvm") version "1.3.50"
  id("org.jetbrains.dokka") version "0.10.1"
  `maven-publish`
}

group = "dev.fuelyour"
version = "0.0.1-SNAPSHOT"

repositories {
  mavenCentral()
  jcenter()
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
  classifier = "sources"
  from(sourceSets["main"].allSource)
}

val dokkaJar by tasks.creating(Jar::class) {
  group = JavaBasePlugin.DOCUMENTATION_GROUP
  description = "Assembles Kotlin docs with Dokka"
  classifier = "javadoc"
  from(tasks.dokka)
}

publishing {
  publications {
    create<MavenPublication>("default") {
      from(components["java"])
      artifact(sourcesJar)
      artifact(dokkaJar)
    }
  }
  repositories {
    maven {
      url = uri("$buildDir/repository")
    }
  }
}
