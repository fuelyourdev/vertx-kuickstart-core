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
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
