plugins {
  kotlin("jvm")
}

val ktorVersion: String by rootProject
val jooqVersion: String by rootProject
val hikariVersion: String by rootProject
val flywayVersion: String by rootProject
val ojdbcVersion: String by rootProject

dependencies {
  api("org.jooq:jooq:$jooqVersion")
  implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
  implementation("com.zaxxer:HikariCP:$hikariVersion")
  implementation("org.flywaydb:flyway-core:$flywayVersion")

  runtimeOnly("com.oracle.database.jdbc:ojdbc8:$ojdbcVersion")
}
