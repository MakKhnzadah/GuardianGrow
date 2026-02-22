import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm") version "1.9.25" apply false
  kotlin("plugin.serialization") version "1.9.25" apply false
}

allprojects {
  group = property("projectGroup") as String
  version = property("projectVersion") as String

  repositories {
    mavenCentral()
  }
}

subprojects {
  tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
      val javaTarget = (rootProject.property("javaTarget") as String)
      jvmTarget = if (javaTarget == "8") "1.8" else javaTarget
      freeCompilerArgs = freeCompilerArgs + listOf(
        "-Xjsr305=strict",
      )
    }
  }
}
