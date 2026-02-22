package com.guardiangrow.data

import io.ktor.server.config.*

data class DbConfig(
  val jdbcUrl: String,
  val username: String,
  val password: String,
){
  companion object {
    fun fromConfig(config: ApplicationConfig): DbConfig {
      val url = config.property("${DbConfigKeys.PREFIX}.jdbcUrl").getString()
      val user = config.property("${DbConfigKeys.PREFIX}.username").getString()
      val pass = config.property("${DbConfigKeys.PREFIX}.password").getString()
      return DbConfig(url, user, pass)
    }
  }
}

object DbConfigKeys {
  const val PREFIX = "db"
}
