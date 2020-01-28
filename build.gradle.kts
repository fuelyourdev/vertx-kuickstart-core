import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm")
}

group = "dev.fuelyour.vertx-kuickstart-core"
version = "1.0-SNAPSHOT"

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
}
