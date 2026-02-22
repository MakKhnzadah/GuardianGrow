plugins {
  application
  kotlin("jvm")
  kotlin("plugin.serialization")
}

val ktorVersion: String by rootProject
val logbackVersion: String by rootProject
val jwtVersion: String by rootProject

dependencies {
  implementation(project(":data-jooq"))
  implementation(project(":db-migrations"))
  implementation(project(":rules-engine"))
  implementation(project(":reporting-engine"))

  implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
  implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-cors-jvm:$ktorVersion")

  implementation("io.ktor:ktor-server-auth-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-auth-jwt-jvm:$ktorVersion")

  implementation("com.auth0:java-jwt:$jwtVersion")

  implementation("ch.qos.logback:logback-classic:$logbackVersion")
}

application {
  mainClass.set("com.guardiangrow.app.ApplicationKt")
}
