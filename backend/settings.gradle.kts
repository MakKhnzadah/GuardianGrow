plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "guardiangrow-backend"

include(
  ":app",
  ":data-jooq",
  ":db-migrations",
  ":rules-engine",
  ":reporting-engine",
)
