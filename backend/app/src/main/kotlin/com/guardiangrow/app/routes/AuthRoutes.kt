package com.guardiangrow.app.routes

import com.guardiangrow.app.JwtService
import com.guardiangrow.app.http.problemDetails
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
private data class RegisterRequest(val email: String, val password: String, val displayName: String)

@Serializable
private data class LoginRequest(val email: String, val password: String)

@Serializable
private data class RefreshRequest(val refreshToken: String)

@Serializable
private data class LogoutRequest(val refreshToken: String)

@Serializable
private data class AuthResponse(
  val userId: String,
  val householdId: String,
  val accessToken: String,
  val refreshToken: String,
)

fun Route.authRoutes(jwtService: JwtService) {
  route("/auth") {
    post("/register") {
      val req = call.receive<RegisterRequest>()
      if (req.email.isBlank() || req.password.isBlank() || req.displayName.isBlank()) {
        call.respond(
          HttpStatusCode.BadRequest,
          problemDetails("Bad Request", 400, "Missing required fields", call.request.path()),
        )
        return@post
      }

      // TODO: persist user + household in Oracle; hash password; write audit log.
      val userId = UUID.randomUUID().toString()
      val householdId = UUID.randomUUID().toString()
      val accessToken = jwtService.issueAccessToken(userId, "PARENT")
      val refreshToken = UUID.randomUUID().toString()

      call.respond(AuthResponse(userId, householdId, accessToken, refreshToken))
    }

    post("/login") {
      val req = call.receive<LoginRequest>()
      if (req.email.isBlank() || req.password.isBlank()) {
        call.respond(
          HttpStatusCode.BadRequest,
          problemDetails("Bad Request", 400, "Missing required fields", call.request.path()),
        )
        return@post
      }

      // TODO: verify password against stored hash; write security event on failure.
      val userId = UUID.randomUUID().toString()
      val householdId = UUID.randomUUID().toString()
      val accessToken = jwtService.issueAccessToken(userId, "PARENT")
      val refreshToken = UUID.randomUUID().toString()

      call.respond(AuthResponse(userId, householdId, accessToken, refreshToken))
    }

    post("/refresh") {
      val req = call.receive<RefreshRequest>()
      if (req.refreshToken.isBlank()) {
        call.respond(HttpStatusCode.BadRequest, problemDetails("Bad Request", 400, "Missing refresh token"))
        return@post
      }
      // TODO: validate refresh token hash from DB, rotate, revoke previous.
      val userId = UUID.randomUUID().toString()
      val accessToken = jwtService.issueAccessToken(userId, "PARENT")
      val refreshToken = UUID.randomUUID().toString()
      call.respond(mapOf("accessToken" to accessToken, "refreshToken" to refreshToken))
    }

    post("/logout") {
      val req = call.receive<LogoutRequest>()
      if (req.refreshToken.isBlank()) {
        call.respond(HttpStatusCode.BadRequest, problemDetails("Bad Request", 400, "Missing refresh token"))
        return@post
      }
      // TODO: revoke refresh token in DB.
      call.respond(HttpStatusCode.OK)
    }
  }
}
