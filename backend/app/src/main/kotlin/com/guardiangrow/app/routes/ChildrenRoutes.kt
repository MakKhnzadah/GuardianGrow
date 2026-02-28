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
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.jooq.impl.DSL
import java.sql.Date

@Serializable
private data class CreateChildRequest(
  val displayName: String,
  val birthDate: String,
  val avatarKey: String? = null,
)

@Serializable
private data class ChildProfileResponse(
  val id: String,
  val displayName: String,
  val birthDate: String,
  val avatarKey: String? = null,
  val status: String,
  val createdAt: String? = null,
)

private object ChildProfilesInMemory {
  // Keyed by householdId.
  private val byHousehold = ConcurrentHashMap<String, MutableList<ChildProfileResponse>>()

  fun list(householdId: String): List<ChildProfileResponse> =
    byHousehold[householdId]?.toList() ?: emptyList()

  fun create(householdId: String, child: ChildProfileResponse): ChildProfileResponse {
    byHousehold.compute(householdId) { _, existing ->
      val list = existing ?: mutableListOf()
      list.add(child)
      list
    }
    return child
  }
}

fun Route.childrenRoutes() {
  route("/households/{householdId}/children") {
    fun requireParentOrAdmin(call: ApplicationCall): Boolean {
      val principal = call.principal<JWTPrincipal>() ?: return false
      val role = principal.payload.getClaim("role").asString()
      return role == "PARENT" || role == "ADMIN"
    }

    fun listFromDb(call: ApplicationCall, householdId: String) : List<ChildProfileResponse> {
      val db = call.databaseOrNull() ?: return emptyList()
      val dsl = db.dsl

      val table = DSL.table(DSL.name("child_profiles"))
      val idF = DSL.field(DSL.name("id"), ByteArray::class.java)
      val householdIdF = DSL.field(DSL.name("household_id"), ByteArray::class.java)
      val displayNameF = DSL.field(DSL.name("display_name"), String::class.java)
      val birthDateF = DSL.field(DSL.name("birth_date"), Date::class.java)
      val avatarKeyF = DSL.field(DSL.name("avatar_key"), String::class.java)
      val statusF = DSL.field(DSL.name("status"), String::class.java)
      val createdAtF = DSL.field(DSL.name("created_at"))

      val hhRaw = uuidToRaw(UUID.fromString(householdId))

      return dsl
        .select(idF, displayNameF, birthDateF, avatarKeyF, statusF, createdAtF)
        .from(table)
        .where(householdIdF.eq(hhRaw))
        .orderBy(createdAtF.desc())
        .fetch()
        .map { r ->
          val idRaw = r.get(idF)!!
          val birthDate = r.get(birthDateF)!!.toLocalDate().toString()
          ChildProfileResponse(
            id = rawToUuid(idRaw).toString(),
            displayName = r.get(displayNameF) ?: "",
            birthDate = birthDate,
            avatarKey = r.get(avatarKeyF),
            status = r.get(statusF) ?: "ACTIVE",
            createdAt = r.get(createdAtF)?.toString(),
          )
        }
    }

    fun createInDb(call: ApplicationCall, householdId: String, req: CreateChildRequest): ChildProfileResponse? {
      val db = call.databaseOrNull() ?: return null
      val dsl = db.dsl

      val households = DSL.table(DSL.name("households"))
      val childProfiles = DSL.table(DSL.name("child_profiles"))
      val idF = DSL.field(DSL.name("id"), ByteArray::class.java)
      val hhIdF = DSL.field(DSL.name("household_id"), ByteArray::class.java)
      val nameF = DSL.field(DSL.name("name"), String::class.java)
      val displayNameF = DSL.field(DSL.name("display_name"), String::class.java)
      val birthDateF = DSL.field(DSL.name("birth_date"), Date::class.java)
      val avatarKeyF = DSL.field(DSL.name("avatar_key"), String::class.java)
      val statusF = DSL.field(DSL.name("status"), String::class.java)

      val hhUuid = UUID.fromString(householdId)
      val hhRaw = uuidToRaw(hhUuid)
      try {
        dsl.insertInto(households)
          .set(idF, hhRaw)
          .set(nameF, "Household")
          .execute()
      } catch (_: Exception) {
        // ignore duplicates
      }

      val birthDate = try {
        LocalDate.parse(req.birthDate)
      } catch (_: Exception) {
        return null
      }

      val childUuid = UUID.randomUUID()
      val childRaw = uuidToRaw(childUuid)
      dsl.insertInto(childProfiles)
        .set(idF, childRaw)
        .set(hhIdF, hhRaw)
        .set(displayNameF, req.displayName)
        .set(birthDateF, Date.valueOf(birthDate))
        .set(avatarKeyF, req.avatarKey)
        .set(statusF, "ACTIVE")
        .execute()

      return ChildProfileResponse(
        id = childUuid.toString(),
        displayName = req.displayName,
        birthDate = birthDate.toString(),
        avatarKey = req.avatarKey,
        status = "ACTIVE",
        createdAt = Instant.now().toString(),
      )
    }

    get {
      if (!requireParentOrAdmin(call)) {
        call.respond(HttpStatusCode.Forbidden, problemDetails("Forbidden", 403, "Insufficient role"))
        return@get
      }

      val householdId = call.parameters["householdId"].orEmpty()
      if (householdId.isBlank()) {
        call.respond(HttpStatusCode.BadRequest, problemDetails("Bad Request", 400, "Missing householdId"))
        return@get
      }

      val db = call.databaseOrNull()
      if (db != null) {
        try {
          call.respond(listFromDb(call, householdId))
        } catch (e: IllegalArgumentException) {
          call.respond(HttpStatusCode.BadRequest, problemDetails("Bad Request", 400, "Invalid householdId"))
        }
        return@get
      }

      call.respond(ChildProfilesInMemory.list(householdId))
    }

    post {
      if (!requireParentOrAdmin(call)) {
        call.respond(HttpStatusCode.Forbidden, problemDetails("Forbidden", 403, "Insufficient role"))
        return@post
      }

      val householdId = call.parameters["householdId"].orEmpty()
      if (householdId.isBlank()) {
        call.respond(HttpStatusCode.BadRequest, problemDetails("Bad Request", 400, "Missing householdId"))
        return@post
      }

      val req = call.receive<CreateChildRequest>()
      if (req.displayName.isBlank() || req.birthDate.isBlank()) {
        call.respond(HttpStatusCode.BadRequest, problemDetails("Bad Request", 400, "Missing required fields"))
        return@post
      }

      val db = call.databaseOrNull()
      if (db != null) {
        try {
          val created = createInDb(call, householdId, req)
          if (created == null) {
            call.respond(HttpStatusCode.BadRequest, problemDetails("Bad Request", 400, "Missing or invalid fields"))
            return@post
          }
          call.respond(HttpStatusCode.Created, created)
        } catch (e: IllegalArgumentException) {
          call.respond(HttpStatusCode.BadRequest, problemDetails("Bad Request", 400, "Invalid householdId"))
        }
        return@post
      }

      val child = ChildProfileResponse(
        id = UUID.randomUUID().toString(),
        displayName = req.displayName,
        birthDate = req.birthDate,
        avatarKey = req.avatarKey,
        status = "ACTIVE",
        createdAt = Instant.now().toString(),
      )

      call.respond(HttpStatusCode.Created, ChildProfilesInMemory.create(householdId, child))
    }
  }
}
