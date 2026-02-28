import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

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
  val javaTarget = (rootProject.property("javaTarget") as String).toInt()

  plugins.withId("java") {
    extensions.getByType<JavaPluginExtension>().toolchain {
      languageVersion.set(JavaLanguageVersion.of(javaTarget))
    }

    val toolchains = extensions.getByType<JavaToolchainService>()
    tasks.withType<JavaExec>().configureEach {
      javaLauncher.set(
        toolchains.launcherFor {
          languageVersion.set(JavaLanguageVersion.of(javaTarget))
        },
      )
    }
  }

  plugins.withId("org.jetbrains.kotlin.jvm") {
    extensions.getByType<KotlinJvmProjectExtension>().jvmToolchain(javaTarget)
  }

  tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
      val target = (rootProject.property("javaTarget") as String)
      jvmTarget = if (target == "8") "1.8" else target
      freeCompilerArgs = freeCompilerArgs + listOf(
        "-Xjsr305=strict",
      )
    }
  }
}
