package com.guardiangrow.app

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.guardiangrow.app.http.problemDetails
import com.guardiangrow.app.routes.authRoutes
import com.guardiangrow.app.routes.healthRoutes
import com.guardiangrow.data.DbConfig
import com.guardiangrow.data.Database
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun main(args: Array<String>) {
  EngineMain.main(args)
}

fun Application.module() {
  val app = this
  val appConfig = environment.config

  install(CallLogging)

  install(ContentNegotiation) {
    json(
      Json {
        ignoreUnknownKeys = true
        explicitNulls = false
      },
    )
  }

  install(CORS) {
    anyHost()
    allowHeader(HttpHeaders.Authorization)
    allowHeader(HttpHeaders.ContentType)
    allowMethod(HttpMethod.Get)
    allowMethod(HttpMethod.Post)
    allowMethod(HttpMethod.Patch)
    allowMethod(HttpMethod.Delete)
  }

  install(StatusPages) {
    exception<Throwable> { call, cause ->
      app.environment.log.error("Unhandled error", cause)
      call.respond(
        status = HttpStatusCode.InternalServerError,
        message = problemDetails(
          title = "Internal Server Error",
          status = 500,
          detail = "Unexpected error",
          instance = call.request.path(),
        ),
      )
    }
  }

  val dbEnabled = appConfig.propertyOrNull("db.enabled")?.getString()?.toBoolean() ?: true
  if (dbEnabled) {
    val dbConfig = DbConfig.fromConfig(appConfig)
    val database = Database(dbConfig)
    environment.monitor.subscribe(ApplicationStopping) {
      database.close()
    }
  }

  val jwtConfig = JwtConfig.fromConfig(appConfig)
  val jwtService = JwtService(jwtConfig)

  install(Authentication) {
    jwt("auth-jwt") {
      realm = jwtConfig.realm
      verifier(
        JWT
          .require(Algorithm.HMAC256(jwtConfig.hmacSecret))
          .withIssuer(jwtConfig.issuer)
          .withAudience(jwtConfig.audience)
          .build(),
      )
      validate { credential ->
        val userId = credential.payload.getClaim("uid").asString()
        val role = credential.payload.getClaim("role").asString()
        if (userId.isNullOrBlank() || role.isNullOrBlank()) null else JWTPrincipal(credential.payload)
      }
      challenge { _, _ ->
        call.respond(
          status = HttpStatusCode.Unauthorized,
          message = problemDetails(
            title = "Unauthorized",
            status = 401,
            detail = "Missing or invalid access token",
            instance = call.request.path(),
          ),
        )
      }
    }
  }

  routing {
    route("/api/v1") {
      healthRoutes()
      authRoutes(jwtService)

      authenticate("auth-jwt") {
        get("/me") {
          val principal = call.principal<JWTPrincipal>()!!
          val userId = principal.payload.getClaim("uid").asString()
          val role = principal.payload.getClaim("role").asString()
          call.respond(mapOf("userId" to userId, "role" to role))
        }
      }
    }
  }
}
