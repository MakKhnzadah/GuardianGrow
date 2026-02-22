package com.guardiangrow.app

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.time.Instant
import java.util.*

class JwtService(private val config: JwtConfig) {
  private val algorithm = Algorithm.HMAC256(config.hmacSecret)

  fun issueAccessToken(userId: String, role: String): String {
    val now = Instant.now()
    val exp = now.plusSeconds(config.accessTokenTtlSeconds)
    return JWT.create()
      .withIssuer(config.issuer)
      .withAudience(config.audience)
      .withSubject(userId)
      .withJWTId(UUID.randomUUID().toString())
      .withIssuedAt(Date.from(now))
      .withExpiresAt(Date.from(exp))
      .withClaim("uid", userId)
      .withClaim("role", role)
      .sign(algorithm)
  }
}
