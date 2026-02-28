package com.guardiangrow.app.routes

import com.guardiangrow.app.db.databaseOrNull
import com.guardiangrow.app.db.rawToUuid
import com.guardiangrow.app.db.uuidToRaw
import com.guardiangrow.app.http.problemDetails
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jooq.impl.DSL
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Serializable
private data class ContentReportResponse(
  val id: String,
  val contentId: String,
  val reason: String,
  val details: String? = null,
  val status: String,
  val createdAt: String,
  val resolvedAt: String? = null,
)

@Serializable
private data class ResolveReportRequest(
  val resolution: String,
  val notes: String? = null,
)

fun Route.reportsRoutes() {
  route("/reports") {
    fun role(call: ApplicationCall): String? = call.principal<JWTPrincipal>()?.payload?.getClaim("role")?.asString()
    fun userId(call: ApplicationCall): String? = call.principal<JWTPrincipal>()?.payload?.getClaim("uid")?.asString()

    fun canRead(call: ApplicationCall): Boolean {
      val r = role(call) ?: return false
      return r == "PARENT" || r == "ADMIN" || r == "MODERATOR"
    }

    fun canResolve(call: ApplicationCall): Boolean {
      val r = role(call) ?: return false
      return r == "ADMIN" || r == "MODERATOR" || r == "PARENT"
    }

    route("/content") {
      get {
        if (!canRead(call)) {
          call.respond(HttpStatusCode.Forbidden, problemDetails("Forbidden", 403, "Insufficient role"))
          return@get
        }

        val db = call.databaseOrNull()
        if (db == null) {
          call.respond(emptyList<ContentReportResponse>())
          return@get
        }

        val statusParam = call.request.queryParameters["status"]?.trim().orEmpty().ifBlank { null }

        val dsl = db.dsl
        val reports = DSL.table(DSL.name("content_reports"))
        val idF = DSL.field(DSL.name("id"), ByteArray::class.java)
        val contentIdF = DSL.field(DSL.name("content_id"), ByteArray::class.java)
        val reasonF = DSL.field(DSL.name("reason"), String::class.java)
        val detailsF = DSL.field(DSL.name("details"), String::class.java)
        val statusF = DSL.field(DSL.name("status"), String::class.java)
        val createdAtF = DSL.field(DSL.name("created_at"), OffsetDateTime::class.java)
        val resolvedAtF = DSL.field(DSL.name("resolved_at"), OffsetDateTime::class.java)

        val condition = statusParam?.let { statusF.eq(it) } ?: DSL.noCondition()

        val rows = dsl
          .select(idF, contentIdF, reasonF, detailsF, statusF, createdAtF, resolvedAtF)
          .from(reports)
          .where(condition)
          .orderBy(createdAtF.desc())
          .fetch()
        call.respond(
          rows.map { r ->
            val idRaw = r.get(idF)!!
            val contentRaw = r.get(contentIdF)!!
            ContentReportResponse(
              id = rawToUuid(idRaw).toString(),
              contentId = rawToUuid(contentRaw).toString(),
              reason = r.get(reasonF) ?: "OTHER",
              details = r.get(detailsF),
              status = r.get(statusF) ?: "OPEN",
              createdAt = (r.get(createdAtF) ?: OffsetDateTime.now(ZoneOffset.UTC)).toString(),
              resolvedAt = r.get(resolvedAtF)?.toString(),
            )
          },
        )
      }

      post("/{reportId}/resolve") {
        if (!canResolve(call)) {
          call.respond(HttpStatusCode.Forbidden, problemDetails("Forbidden", 403, "Insufficient role"))
          return@post
        }

        val db = call.databaseOrNull()
        if (db == null) {
          call.respond(HttpStatusCode.BadRequest, problemDetails("Bad Request", 400, "DB is disabled"))
          return@post
        }

        val reportId = call.parameters["reportId"].orEmpty()
        val reportUuid = try {
          UUID.fromString(reportId)
        } catch (_: Exception) {
          call.respond(HttpStatusCode.BadRequest, problemDetails("Bad Request", 400, "Invalid reportId"))
          return@post
        }

        val req = call.receive<ResolveReportRequest>()
        val nextStatus = when (req.resolution.uppercase()) {
          "RESOLVE" -> "RESOLVED"
          "DISMISS" -> "DISMISSED"
          else -> null
        }
        if (nextStatus == null) {
          call.respond(HttpStatusCode.BadRequest, problemDetails("Bad Request", 400, "Invalid resolution"))
          return@post
        }

        val userUuid = try {
          userId(call)?.let { UUID.fromString(it) }
        } catch (_: Exception) {
          null
        }

        val dsl = db.dsl
        val reports = DSL.table(DSL.name("content_reports"))
        val idF = DSL.field(DSL.name("id"), ByteArray::class.java)
        val statusF = DSL.field(DSL.name("status"), String::class.java)
        val resolvedAtF = DSL.field(DSL.name("resolved_at"), OffsetDateTime::class.java)
        val resolvedByF = DSL.field(DSL.name("resolved_by"), ByteArray::class.java)

        val updated = dsl.update(reports)
          .set(statusF, nextStatus)
          .set(resolvedAtF, OffsetDateTime.now(ZoneOffset.UTC))
          .apply {
            if (userUuid != null) set(resolvedByF, uuidToRaw(userUuid))
          }
          .where(idF.eq(uuidToRaw(reportUuid)))
          .execute()

        if (updated == 0) {
          call.respond(HttpStatusCode.NotFound, problemDetails("Not Found", 404, "Report not found"))
          return@post
        }

        call.respond(HttpStatusCode.NoContent)
      }
    }
  }
}
