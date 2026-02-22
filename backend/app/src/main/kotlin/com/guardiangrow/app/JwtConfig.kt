package com.guardiangrow.app

import io.ktor.server.config.*

data class JwtConfig(
  val issuer: String,
  val audience: String,
  val realm: String,
  val accessTokenTtlSeconds: Long,
  val hmacSecret: String,
){
  companion object {
    fun fromConfig(config: ApplicationConfig): JwtConfig {
      val issuer = config.property("${JwtConfigKeys.PREFIX}.issuer").getString()
      val audience = config.property("${JwtConfigKeys.PREFIX}.audience").getString()
      val realm = config.property("${JwtConfigKeys.PREFIX}.realm").getString()
      val ttl = config.property("${JwtConfigKeys.PREFIX}.accessTokenTtlSeconds").getString().toLong()
      val secret = (System.getenv("GG_JWT_SECRET") ?: "dev-secret-change-me")
      return JwtConfig(issuer, audience, realm, ttl, secret)
    }
  }
}

object JwtConfigKeys {
  const val PREFIX = "app.jwt"
}
