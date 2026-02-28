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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.jooq.impl.DSL

@Serializable
private data class StartSessionRequest(
  val planItemId: String,
  val clientTime: String,
)

@Serializable
private data class BreakPolicyResponse(
  val breakAfterMin: Int,
  val breakDurationMin: Int,
)

@Serializable
private data class SessionStartResponse(
  val sessionId: String,
  val state: String,
  val hardStopAt: String,
  val resumeAt: String? = null,
  val breakPolicy: BreakPolicyResponse,
  val content: ContentSummaryResponse,
)

@Serializable
private data class ContentSummaryResponse(
  val contentId: String,
  val title: String,
)

@Serializable
private data class HeartbeatRequest(
  val elapsedSec: Int,
  val active: Boolean,
)

@Serializable
private data class HeartbeatResponse(
  val state: String,
  val resumeAt: String? = null,
)

@Serializable
private data class PostEventRequest(
  val type: String,
  val meta: Map<String, JsonElement>? = null,
)

@Serializable
private data class EndSessionRequest(
  val reason: String,
)

private data class SessionRecord(
  val id: String,
  val childId: String,
  val planItemId: String,
  val contentId: String,
  val contentTitle: String,
  val breakAfterMin: Int,
  val breakDurationMin: Int,
  var state: String,
  val hardStopAt: Instant,
  var resumeAt: Instant? = null,
  val startedAtServer: Instant,
  var endedAtServer: Instant? = null,
  var lastHeartbeatAt: Instant? = null,
)

private object SessionsInMemory {
  private val byId = ConcurrentHashMap<String, SessionRecord>()

  fun create(record: SessionRecord): SessionRecord {
    byId[record.id] = record
    return record
  }

  fun get(sessionId: String): SessionRecord? = byId[sessionId]
}

fun Route.sessionsRoutes() {
  fun role(call: ApplicationCall): String? = call.principal<JWTPrincipal>()?.payload?.getClaim("role")?.asString()
  fun userId(call: ApplicationCall): String? = call.principal<JWTPrincipal>()?.payload?.getClaim("uid")?.asString()

  fun canChildUse(call: ApplicationCall): Boolean {
    val r = role(call) ?: return false
    return r == "CHILD" || r == "PARENT" || r == "ADMIN"
  }

  fun parseClientInstantOrNow(raw: String, now: Instant): Instant {
    return try {
      Instant.parse(raw)
    } catch (_: Exception) {
      now
    }
  }

  fun currentWeekStartUtc(now: Instant): LocalDate {
    val date = now.atZone(ZoneOffset.UTC).toLocalDate()
    return date.with(java.time.DayOfWeek.MONDAY)
  }

  route("/children/{childId}/sessions") {
    post("/start") {
      if (!canChildUse(call)) {
        call.respond(HttpStatusCode.Forbidden, problemDetails("Forbidden", 403, "Insufficient role"))
        return@post
      }

      val childId = call.parameters["childId"].orEmpty()
      if (childId.isBlank()) {
        call.respond(HttpStatusCode.BadRequest, problemDetails("Bad Request", 400, "Missing childId"))
        return@post
      }

      val req = call.receive<StartSessionRequest>()
      if (req.planItemId.isBlank() || req.clientTime.isBlank()) {
        call.respond(HttpStatusCode.BadRequest, problemDetails("Bad Request", 400, "Missing required fields"))
        return@post
      }

      val now = Instant.now()

      val db = call.databaseOrNull()
      if (db != null) {
        val dsl = db.dsl
        val childUuid = try {
          UUID.fromString(childId)
        } catch (_: Exception) {
          call.respond(HttpStatusCode.BadRequest, problemDetails("Bad Request", 400, "Invalid childId"))
          return@post
        }
        val planItemUuid = try {
          UUID.fromString(req.planItemId)
        } catch (_: Exception) {
          call.respond(HttpStatusCode.BadRequest, problemDetails("Bad Request", 400, "Invalid planItemId"))
          return@post
        }

        val planItems = DSL.table(DSL.name("plan_items"))
        val learningPlans = DSL.table(DSL.name("learning_plans"))
        val contentItems = DSL.table(DSL.name("content_items"))
        val childProfiles = DSL.table(DSL.name("child_profiles"))
        val sessions = DSL.table(DSL.name("sessions"))
        val sessionEvents = DSL.table(DSL.name("session_events"))

        val piIdF = DSL.field(DSL.name("id"), ByteArray::class.java)
        val piPlanIdF = DSL.field(DSL.name("plan_id"), ByteArray::class.java)
        val piContentIdF = DSL.field(DSL.name("content_id"), ByteArray::class.java)
        val piTargetMinutesF = DSL.field(DSL.name("target_minutes"), Int::class.java)

        val lpIdF = DSL.field(DSL.name("id"), ByteArray::class.java)
        val lpChildIdF = DSL.field(DSL.name("child_id"), ByteArray::class.java)
        val lpBreakAfterF = DSL.field(DSL.name("break_after_min"), Int::class.java)
        val lpBreakDurationF = DSL.field(DSL.name("break_duration_min"), Int::class.java)

        val cpIdF = DSL.field(DSL.name("id"), ByteArray::class.java)
        val cpHouseholdIdF = DSL.field(DSL.name("household_id"), ByteArray::class.java)

        val sIdF = DSL.field(DSL.name("id"), ByteArray::class.java)
        val sHouseholdIdF = DSL.field(DSL.name("household_id"), ByteArray::class.java)
        val sChildIdF = DSL.field(DSL.name("child_id"), ByteArray::class.java)
        val sPlanItemIdF = DSL.field(DSL.name("plan_item_id"), ByteArray::class.java)
        val sContentIdF = DSL.field(DSL.name("content_id"), ByteArray::class.java)
        val sStartedAtF = DSL.field(DSL.name("started_at"), OffsetDateTime::class.java)
        val sStateF = DSL.field(DSL.name("state"), String::class.java)

        val seIdF = DSL.field(DSL.name("id"), ByteArray::class.java)
        val seSessionIdF = DSL.field(DSL.name("session_id"), ByteArray::class.java)
        val seEventTimeF = DSL.field(DSL.name("event_time"), OffsetDateTime::class.java)
        val seTypeF = DSL.field(DSL.name("type"), String::class.java)
        val seMetaF = DSL.field(DSL.name("meta_json"), String::class.java)

        val ciIdF = DSL.field(DSL.name("id"), ByteArray::class.java)
        val ciTitleF = DSL.field(DSL.name("title"), String::class.java)

        val childRaw = uuidToRaw(childUuid)
        val planItemRaw = uuidToRaw(planItemUuid)

        val row = dsl
          .select(piContentIdF, piTargetMinutesF, lpBreakAfterF, lpBreakDurationF)
          .from(planItems)
          .join(learningPlans).on(lpIdF.eq(piPlanIdF))
          .where(piIdF.eq(planItemRaw))
          .and(lpChildIdF.eq(childRaw))
          .fetchOne()

        if (row == null) {
          call.respond(HttpStatusCode.NotFound, problemDetails("Not Found", 404, "Plan item not found"))
          return@post
        }

        val contentRaw = row.get(piContentIdF)!!
        val targetMinutes = row.get(piTargetMinutesF) ?: 1
        val breakAfterMin = row.get(lpBreakAfterF) ?: 20
        val breakDurationMin = row.get(lpBreakDurationF) ?: 5

        val title = dsl.select(ciTitleF)
          .from(contentItems)
          .where(ciIdF.eq(contentRaw))
          .fetchOne(ciTitleF) ?: "Planned content"

        val householdRaw = dsl.select(cpHouseholdIdF)
          .from(childProfiles)
          .where(cpIdF.eq(childRaw))
          .fetchOne(cpHouseholdIdF)
        if (householdRaw == null) {
          call.respond(HttpStatusCode.NotFound, problemDetails("Not Found", 404, "Child not found"))
          return@post
        }

        val clientStart = parseClientInstantOrNow(req.clientTime, now)
        val hardStopAt = clientStart.plusSeconds((targetMinutes.coerceAtLeast(1) * 60).toLong())

        val sessionUuid = UUID.randomUUID()
        val sessionRaw = uuidToRaw(sessionUuid)
        val startedAt = OffsetDateTime.now(ZoneOffset.UTC)

        dsl.insertInto(sessions)
          .columns(sIdF, sHouseholdIdF, sChildIdF, sPlanItemIdF, sContentIdF, sStartedAtF, sStateF)
          .values(sessionRaw, householdRaw, childRaw, planItemRaw, contentRaw, startedAt, "ACTIVE")
          .execute()

        val uid = try {
          userId(call)?.let { UUID.fromString(it) }
        } catch (_: Exception) {
          null
        }
        val meta = buildMap<String, JsonElement> {
          put("planItemId", kotlinx.serialization.json.JsonPrimitive(req.planItemId))
          put("childId", kotlinx.serialization.json.JsonPrimitive(childId))
          put("contentId", kotlinx.serialization.json.JsonPrimitive(rawToUuid(contentRaw).toString()))
          if (uid != null) put("userId", kotlinx.serialization.json.JsonPrimitive(uid.toString()))
        }
        dsl.insertInto(sessionEvents)
          .columns(seIdF, seSessionIdF, seEventTimeF, seTypeF, seMetaF)
          .values(
            uuidToRaw(UUID.randomUUID()),
            sessionRaw,
            startedAt,
            "START",
            Json.encodeToString(JsonObject.serializer(), JsonObject(meta)),
          )
          .execute()

        call.respond(
          SessionStartResponse(
            sessionId = sessionUuid.toString(),
            state = "ACTIVE",
            hardStopAt = hardStopAt.toString(),
            resumeAt = null,
            breakPolicy = BreakPolicyResponse(
              breakAfterMin = breakAfterMin,
              breakDurationMin = breakDurationMin,
            ),
            content = ContentSummaryResponse(
              contentId = rawToUuid(contentRaw).toString(),
              title = title,
            ),
          ),
        )
        return@post
      }

      val weekStart = currentWeekStartUtc(now)
      val planItem = lookupPlanItemSessionInfo(childId, req.planItemId, weekStart)
      if (planItem == null) {
        call.respond(HttpStatusCode.NotFound, problemDetails("Not Found", 404, "Plan item not found"))
        return@post
      }

      val clientStart = parseClientInstantOrNow(req.clientTime, now)
      val hardStopAt = clientStart.plusSeconds((planItem.targetMinutes.coerceAtLeast(1) * 60).toLong())
      val title = lookupContentTitle(planItem.contentId) ?: "Planned content"

      val record = SessionRecord(
        id = UUID.randomUUID().toString(),
        childId = childId,
        planItemId = req.planItemId,
        contentId = planItem.contentId,
        contentTitle = title,
        breakAfterMin = planItem.breakAfterMin,
        breakDurationMin = planItem.breakDurationMin,
        state = "ACTIVE",
        hardStopAt = hardStopAt,
        startedAtServer = now,
      )
      SessionsInMemory.create(record)

      call.respond(
        SessionStartResponse(
          sessionId = record.id,
          state = record.state,
          hardStopAt = record.hardStopAt.toString(),
          resumeAt = record.resumeAt?.toString(),
          breakPolicy = BreakPolicyResponse(
            breakAfterMin = record.breakAfterMin,
            breakDurationMin = record.breakDurationMin,
          ),
          content = ContentSummaryResponse(
            contentId = record.contentId,
            title = record.contentTitle,
          ),
        ),
      )
    }
  }

  route("/sessions/{sessionId}") {
    post("/heartbeat") {
      if (!canChildUse(call)) {
        call.respond(HttpStatusCode.Forbidden, problemDetails("Forbidden", 403, "Insufficient role"))
        return@post
      }

      val sessionId = call.parameters["sessionId"].orEmpty()
      if (sessionId.isBlank()) {
        call.respond(HttpStatusCode.BadRequest, problemDetails("Bad Request", 400, "Missing sessionId"))
        return@post
      }

      val req = call.receive<HeartbeatRequest>()
      val now = Instant.now()

      val db = call.databaseOrNull()
      if (db != null) {
        val sessionUuid = try {
          UUID.fromString(sessionId)
        } catch (_: Exception) {
          call.respond(HttpStatusCode.BadRequest, problemDetails("Bad Request", 400, "Invalid sessionId"))
          return@post
        }

        val dsl = db.dsl
        val sessions = DSL.table(DSL.name("sessions"))
        val sessionEvents = DSL.table(DSL.name("session_events"))
        val planItems = DSL.table(DSL.name("plan_items"))
        val learningPlans = DSL.table(DSL.name("learning_plans"))

        val sIdF = DSL.field(DSL.name("id"), ByteArray::class.java)
        val sStateF = DSL.field(DSL.name("state"), String::class.java)
        val sEndedAtF = DSL.field(DSL.name("ended_at"), OffsetDateTime::class.java)
        val sPlanItemIdF = DSL.field(DSL.name("plan_item_id"), ByteArray::class.java)

        val piIdF = DSL.field(DSL.name("id"), ByteArray::class.java)
        val piPlanIdF = DSL.field(DSL.name("plan_id"), ByteArray::class.java)

        val lpIdF = DSL.field(DSL.name("id"), ByteArray::class.java)
        val lpBreakAfterF = DSL.field(DSL.name("break_after_min"), Int::class.java)
        val lpBreakDurationF = DSL.field(DSL.name("break_duration_min"), Int::class.java)

        val seIdF = DSL.field(DSL.name("id"), ByteArray::class.java)
        val seSessionIdF = DSL.field(DSL.name("session_id"), ByteArray::class.java)
        val seEventTimeF = DSL.field(DSL.name("event_time"), OffsetDateTime::class.java)
        val seTypeF = DSL.field(DSL.name("type"), String::class.java)
        val seMetaF = DSL.field(DSL.name("meta_json"), String::class.java)

        val sessionRaw = uuidToRaw(sessionUuid)

        val row = dsl
          .select(sStateF, sEndedAtF, sPlanItemIdF, lpBreakAfterF, lpBreakDurationF)
          .from(sessions)
          .leftJoin(planItems).on(piIdF.eq(sPlanItemIdF))
          .leftJoin(learningPlans).on(lpIdF.eq(piPlanIdF))
          .where(sIdF.eq(sessionRaw))
          .fetchOne()

        if (row == null) {
          call.respond(HttpStatusCode.NotFound, problemDetails("Not Found", 404, "Session not found"))
          return@post
        }

        var state = row.get(sStateF) ?: "ACTIVE"
        val endedAt = row.get(sEndedAtF)
        if (endedAt != null) state = "ENDED"

        val breakAfterMin = row.get(lpBreakAfterF) ?: 0
        val breakDurationMin = row.get(lpBreakDurationF) ?: 0

        fun latestResumeAt(): Instant? {
          val meta = dsl.select(seMetaF)
            .from(sessionEvents)
            .where(seSessionIdF.eq(sessionRaw))
            .and(seTypeF.eq("FORCED_BREAK_START"))
            .orderBy(seEventTimeF.desc())
            .fetchOne(seMetaF) ?: return null

          return try {
            val resumeAtRaw = Json.parseToJsonElement(meta).jsonObject["resumeAt"]?.jsonPrimitive?.content
            resumeAtRaw?.let { Instant.parse(it) }
          } catch (_: Exception) {
            null
          }
        }

        var resumeAt: Instant? = null
        if (state != "ENDED") {
          if (state == "FORCED_BREAK") {
            resumeAt = latestResumeAt()
            if (resumeAt != null && !now.isBefore(resumeAt)) {
              state = "ACTIVE"
              dsl.update(sessions)
                .set(sStateF, state)
                .where(sIdF.eq(sessionRaw))
                .execute()

              dsl.insertInto(sessionEvents)
                .columns(seIdF, seSessionIdF, seEventTimeF, seTypeF, seMetaF)
                .values(uuidToRaw(UUID.randomUUID()), sessionRaw, OffsetDateTime.now(ZoneOffset.UTC), "BREAK_END", null)
                .execute()
              resumeAt = null
            }
          }

          val breakAfterSec = (breakAfterMin.coerceAtLeast(0) * 60).toLong()
          val breakDurationSec = (breakDurationMin.coerceAtLeast(0) * 60).toLong()
          if (
            state == "ACTIVE" &&
            breakAfterMin > 0 &&
            breakDurationSec > 0 &&
            req.active &&
            req.elapsedSec.toLong() >= breakAfterSec
          ) {
            state = "FORCED_BREAK"
            resumeAt = now.plusSeconds(breakDurationSec)

            dsl.update(sessions)
              .set(sStateF, state)
              .where(sIdF.eq(sessionRaw))
              .execute()

            val meta = Json.encodeToString(
              JsonObject.serializer(),
              JsonObject(
                mapOf(
                  "resumeAt" to kotlinx.serialization.json.JsonPrimitive(resumeAt.toString()),
                  "breakDurationSec" to kotlinx.serialization.json.JsonPrimitive(breakDurationSec),
                ),
              ),
            )
            dsl.insertInto(sessionEvents)
              .columns(seIdF, seSessionIdF, seEventTimeF, seTypeF, seMetaF)
              .values(uuidToRaw(UUID.randomUUID()), sessionRaw, OffsetDateTime.now(ZoneOffset.UTC), "FORCED_BREAK_START", meta)
              .execute()
          }
        }

        val hbMeta = Json.encodeToString(
          JsonObject.serializer(),
          JsonObject(
            mapOf(
              "elapsedSec" to kotlinx.serialization.json.JsonPrimitive(req.elapsedSec),
              "active" to kotlinx.serialization.json.JsonPrimitive(req.active),
            ),
          ),
        )
        dsl.insertInto(sessionEvents)
          .columns(seIdF, seSessionIdF, seEventTimeF, seTypeF, seMetaF)
          .values(uuidToRaw(UUID.randomUUID()), sessionRaw, OffsetDateTime.now(ZoneOffset.UTC), "HEARTBEAT", hbMeta)
          .execute()

        call.respond(
          HeartbeatResponse(
            state = state,
            resumeAt = resumeAt?.toString(),
          ),
        )
        return@post
      }

      val session = SessionsInMemory.get(sessionId)
      if (session == null) {
        call.respond(HttpStatusCode.NotFound, problemDetails("Not Found", 404, "Session not found"))
        return@post
      }

      session.lastHeartbeatAt = now

      if (session.state != "ENDED") {
        if (session.state == "FORCED_BREAK") {
          val resumeAt = session.resumeAt
          if (resumeAt != null && !now.isBefore(resumeAt)) {
            session.state = "ACTIVE"
            session.resumeAt = null
          }
        }

        val breakAfterSec = (session.breakAfterMin.coerceAtLeast(0) * 60).toLong()
        val breakDurationSec = (session.breakDurationMin.coerceAtLeast(0) * 60).toLong()
        if (
          session.state == "ACTIVE" &&
          session.breakAfterMin > 0 &&
          breakDurationSec > 0 &&
          req.active &&
          req.elapsedSec.toLong() >= breakAfterSec
        ) {
          session.state = "FORCED_BREAK"
          session.resumeAt = now.plusSeconds(breakDurationSec)
        }
      }

      call.respond(
        HeartbeatResponse(
          state = session.state,
          resumeAt = session.resumeAt?.toString(),
        ),
      )
    }

    post("/events") {
      if (!canChildUse(call)) {
        call.respond(HttpStatusCode.Forbidden, problemDetails("Forbidden", 403, "Insufficient role"))
        return@post
      }

      val sessionId = call.parameters["sessionId"].orEmpty()
      if (sessionId.isBlank()) {
        call.respond(HttpStatusCode.BadRequest, problemDetails("Bad Request", 400, "Missing sessionId"))
        return@post
      }

      val req = call.receive<PostEventRequest>()
      if (req.type.isBlank()) {
        call.respond(HttpStatusCode.BadRequest, problemDetails("Bad Request", 400, "Missing event type"))
        return@post
      }

      val db = call.databaseOrNull()
      if (db != null) {
        val sessionUuid = try {
          UUID.fromString(sessionId)
        } catch (_: Exception) {
          call.respond(HttpStatusCode.BadRequest, problemDetails("Bad Request", 400, "Invalid sessionId"))
          return@post
        }

        val dsl = db.dsl
        val sessions = DSL.table(DSL.name("sessions"))
        val sessionEvents = DSL.table(DSL.name("session_events"))

        val sIdF = DSL.field(DSL.name("id"), ByteArray::class.java)

        val seIdF = DSL.field(DSL.name("id"), ByteArray::class.java)
        val seSessionIdF = DSL.field(DSL.name("session_id"), ByteArray::class.java)
        val seEventTimeF = DSL.field(DSL.name("event_time"), OffsetDateTime::class.java)
        val seTypeF = DSL.field(DSL.name("type"), String::class.java)
        val seMetaF = DSL.field(DSL.name("meta_json"), String::class.java)

        val sessionRaw = uuidToRaw(sessionUuid)
        val exists = dsl.fetchExists(dsl.selectOne().from(sessions).where(sIdF.eq(sessionRaw)))
        if (!exists) {
          call.respond(HttpStatusCode.NotFound, problemDetails("Not Found", 404, "Session not found"))
          return@post
        }

        val metaJson = req.meta?.let { Json.encodeToString(JsonObject.serializer(), JsonObject(it)) }
        dsl.insertInto(sessionEvents)
          .columns(seIdF, seSessionIdF, seEventTimeF, seTypeF, seMetaF)
          .values(uuidToRaw(UUID.randomUUID()), sessionRaw, OffsetDateTime.now(ZoneOffset.UTC), req.type, metaJson)
          .execute()

        call.respond(HttpStatusCode.NoContent)
        return@post
      }

      val session = SessionsInMemory.get(sessionId)
      if (session == null) {
        call.respond(HttpStatusCode.NotFound, problemDetails("Not Found", 404, "Session not found"))
        return@post
      }

      call.respond(HttpStatusCode.NoContent)
    }

    post("/end") {
      if (!canChildUse(call)) {
        call.respond(HttpStatusCode.Forbidden, problemDetails("Forbidden", 403, "Insufficient role"))
        return@post
      }

      val sessionId = call.parameters["sessionId"].orEmpty()
      if (sessionId.isBlank()) {
        call.respond(HttpStatusCode.BadRequest, problemDetails("Bad Request", 400, "Missing sessionId"))
        return@post
      }

      val req = call.receive<EndSessionRequest>()
      if (req.reason.isBlank()) {
        call.respond(HttpStatusCode.BadRequest, problemDetails("Bad Request", 400, "Missing reason"))
        return@post
      }

      val db = call.databaseOrNull()
      if (db != null) {
        val sessionUuid = try {
          UUID.fromString(sessionId)
        } catch (_: Exception) {
          call.respond(HttpStatusCode.BadRequest, problemDetails("Bad Request", 400, "Invalid sessionId"))
          return@post
        }

        val dsl = db.dsl
        val sessions = DSL.table(DSL.name("sessions"))
        val sessionEvents = DSL.table(DSL.name("session_events"))

        val sIdF = DSL.field(DSL.name("id"), ByteArray::class.java)
        val sStateF = DSL.field(DSL.name("state"), String::class.java)
        val sEndedAtF = DSL.field(DSL.name("ended_at"), OffsetDateTime::class.java)
        val sEndReasonF = DSL.field(DSL.name("end_reason"), String::class.java)

        val seIdF = DSL.field(DSL.name("id"), ByteArray::class.java)
        val seSessionIdF = DSL.field(DSL.name("session_id"), ByteArray::class.java)
        val seEventTimeF = DSL.field(DSL.name("event_time"), OffsetDateTime::class.java)
        val seTypeF = DSL.field(DSL.name("type"), String::class.java)
        val seMetaF = DSL.field(DSL.name("meta_json"), String::class.java)

        val sessionRaw = uuidToRaw(sessionUuid)
        val endedAt = OffsetDateTime.now(ZoneOffset.UTC)
        val updated = dsl.update(sessions)
          .set(sStateF, "ENDED")
          .set(sEndedAtF, endedAt)
          .set(sEndReasonF, req.reason)
          .where(sIdF.eq(sessionRaw))
          .execute()

        if (updated == 0) {
          call.respond(HttpStatusCode.NotFound, problemDetails("Not Found", 404, "Session not found"))
          return@post
        }

        val meta = Json.encodeToString(
          JsonObject.serializer(),
          JsonObject(
            mapOf(
              "reason" to kotlinx.serialization.json.JsonPrimitive(req.reason),
            ),
          ),
        )
        dsl.insertInto(sessionEvents)
          .columns(seIdF, seSessionIdF, seEventTimeF, seTypeF, seMetaF)
          .values(uuidToRaw(UUID.randomUUID()), sessionRaw, endedAt, "END", meta)
          .execute()

        call.respond(HttpStatusCode.NoContent)
        return@post
      }

      val session = SessionsInMemory.get(sessionId)
      if (session == null) {
        call.respond(HttpStatusCode.NotFound, problemDetails("Not Found", 404, "Session not found"))
        return@post
      }

      session.state = "ENDED"
      session.endedAtServer = Instant.now()
      call.respond(HttpStatusCode.NoContent)
    }
  }
}
