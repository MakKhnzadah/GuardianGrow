plugins {
  `java-library`
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of((rootProject.property("javaTarget") as String).toInt()))
  }
}
