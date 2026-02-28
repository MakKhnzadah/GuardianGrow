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
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.jooq.impl.DSL
import java.sql.Date

@Serializable
private data class PlanItemResponse(
  val id: String,
  val contentId: String,
  val dayOfWeek: String,
  val targetMinutes: Int,
  val orderIndex: Int,
  val allowedStart: String? = null,
  val allowedEnd: String? = null,
)

@Serializable
private data class PlanResponse(
  val id: String,
  val childId: String,
  val weekStart: String,
  val dailyTimeLimitMin: Int,
  val breakAfterMin: Int,
  val breakDurationMin: Int,
  val status: String,
  val items: List<PlanItemResponse>,
)

@Serializable
private data class CreatePlanItemRequest(
  val contentId: String,
  val dayOfWeek: String,
  val targetMinutes: Int,
  val allowedStart: String? = null,
  val allowedEnd: String? = null,
)

private data class PlanState(
  val id: String,
  val childId: String,
  val weekStart: LocalDate,
  val dailyTimeLimitMin: Int,
  val breakAfterMin: Int,
  val breakDurationMin: Int,
  val status: String,
  val items: MutableList<PlanItemResponse>,
)

private object PlansInMemory {
  private val byChildId = ConcurrentHashMap<String, PlanState>()

  fun getOrCreate(childId: String, weekStart: LocalDate): PlanState {
    return byChildId.compute(childId) { _, existing ->
      if (existing != null && existing.weekStart == weekStart) existing
      else {
        PlanState(
          id = UUID.randomUUID().toString(),
          childId = childId,
          weekStart = weekStart,
          dailyTimeLimitMin = 45,
          breakAfterMin = 20,
          breakDurationMin = 5,
          status = "ACTIVE",
          items = mutableListOf(),
        )
      }
    }!!
  }

  fun addItem(plan: PlanState, req: CreatePlanItemRequest): PlanItemResponse {
    val day = req.dayOfWeek.uppercase()
    val nextIndex = (plan.items.filter { it.dayOfWeek == day }.maxOfOrNull { it.orderIndex } ?: 0) + 1
    val item = PlanItemResponse(
      id = UUID.randomUUID().toString(),
      contentId = req.contentId,
      dayOfWeek = day,
      targetMinutes = req.targetMinutes,
      orderIndex = nextIndex,
      allowedStart = req.allowedStart,
      allowedEnd = req.allowedEnd,
    )
    plan.items.add(item)
    return item
  }
}

internal data class PlanItemSessionInfo(
  val contentId: String,
  val targetMinutes: Int,
  val breakAfterMin: Int,
  val breakDurationMin: Int,
)

internal fun lookupPlanItemSessionInfo(childId: String, planItemId: String, weekStart: LocalDate): PlanItemSessionInfo? {
  val plan = PlansInMemory.getOrCreate(childId, weekStart)
  val item = plan.items.firstOrNull { it.id == planItemId } ?: return null
  return PlanItemSessionInfo(
    contentId = item.contentId,
    targetMinutes = item.targetMinutes,
    breakAfterMin = plan.breakAfterMin,
    breakDurationMin = plan.breakDurationMin,
  )
}

fun Route.plansRoutes() {
  route("/children/{childId}/plan") {
    fun role(call: ApplicationCall): String? = call.principal<JWTPrincipal>()?.payload?.getClaim("role")?.asString()

    fun canRead(call: ApplicationCall): Boolean {
      val r = role(call) ?: return false
      return r == "CHILD" || r == "PARENT" || r == "ADMIN"
    }

    fun canWrite(call: ApplicationCall): Boolean {
      val r = role(call) ?: return false
      return r == "PARENT" || r == "ADMIN"
    }

    fun parseWeekStart(raw: String?): LocalDate {
      if (raw.isNullOrBlank()) return LocalDate.now().with(java.time.DayOfWeek.MONDAY)
      return try {
        LocalDate.parse(raw)
      } catch (_: DateTimeParseException) {
        LocalDate.now().with(java.time.DayOfWeek.MONDAY)
      }
    }

    get {
      if (!canRead(call)) {
        call.respond(HttpStatusCode.Forbidden, problemDetails("Forbidden", 403, "Insufficient role"))
        return@get
      }
      val childId = call.parameters["childId"].orEmpty()
      if (childId.isBlank()) {
        call.respond(HttpStatusCode.BadRequest, problemDetails("Bad Request", 400, "Missing childId"))
        return@get
      }

      val weekStart = parseWeekStart(call.request.queryParameters["weekStart"])

      val db = call.databaseOrNull()
      if (db != null) {
        val dsl = db.dsl
        val childUuid = try {
          UUID.fromString(childId)
        } catch (_: Exception) {
          call.respond(HttpStatusCode.BadRequest, problemDetails("Bad Request", 400, "Invalid childId"))
          return@get
        }

        val childProfiles = DSL.table(DSL.name("child_profiles"))
        val learningPlans = DSL.table(DSL.name("learning_plans"))
        val planItems = DSL.table(DSL.name("plan_items"))

        val idF = DSL.field(DSL.name("id"), ByteArray::class.java)
        val householdIdF = DSL.field(DSL.name("household_id"), ByteArray::class.java)
        val childIdF = DSL.field(DSL.name("child_id"), ByteArray::class.java)
        val nameF = DSL.field(DSL.name("name"), String::class.java)
        val weekStartF = DSL.field(DSL.name("week_start"), Date::class.java)
        val dailyLimitF = DSL.field(DSL.name("daily_time_limit_min"), Int::class.java)
        val breakAfterF = DSL.field(DSL.name("break_after_min"), Int::class.java)
        val breakDurationF = DSL.field(DSL.name("break_duration_min"), Int::class.java)
        val statusF = DSL.field(DSL.name("status"), String::class.java)

        val planIdF = DSL.field(DSL.name("plan_id"), ByteArray::class.java)
        val contentIdF = DSL.field(DSL.name("content_id"), ByteArray::class.java)
        val dayOfWeekF = DSL.field(DSL.name("day_of_week"), String::class.java)
        val targetMinutesF = DSL.field(DSL.name("target_minutes"), Int::class.java)
        val orderIndexF = DSL.field(DSL.name("order_index"), Int::class.java)
        val allowedStartF = DSL.field(DSL.name("allowed_start"), String::class.java)
        val allowedEndF = DSL.field(DSL.name("allowed_end"), String::class.java)

        val childRaw = uuidToRaw(childUuid)
        val hhRaw = dsl.select(householdIdF)
          .from(childProfiles)
          .where(idF.eq(childRaw))
          .fetchOne(householdIdF)

        if (hhRaw == null) {
          call.respond(HttpStatusCode.NotFound, problemDetails("Not Found", 404, "Child not found"))
          return@get
        }

        val weekSql = Date.valueOf(weekStart)

        var plan = dsl.select(idF, dailyLimitF, breakAfterF, breakDurationF, statusF)
          .from(learningPlans)
          .where(childIdF.eq(childRaw))
          .and(weekStartF.eq(weekSql))
          .fetchOne()

        if (plan == null) {
          val planUuid = UUID.randomUUID()
          val planRaw = uuidToRaw(planUuid)
          dsl.insertInto(learningPlans)
            .set(idF, planRaw)
            .set(householdIdF, hhRaw)
            .set(childIdF, childRaw)
            .set(nameF, "Weekly plan")
            .set(weekStartF, weekSql)
            .set(dailyLimitF, 45)
            .set(breakAfterF, 20)
            .set(breakDurationF, 5)
            .set(statusF, "ACTIVE")
            .execute()

          plan = dsl.select(idF, dailyLimitF, breakAfterF, breakDurationF, statusF)
            .from(learningPlans)
            .where(idF.eq(planRaw))
            .fetchOne()
        }

        val planRaw = plan!!.get(idF)!!
        val items = dsl.select(idF, contentIdF, dayOfWeekF, targetMinutesF, orderIndexF, allowedStartF, allowedEndF)
          .from(planItems)
          .where(planIdF.eq(planRaw))
          .fetch()
          .map { r ->
            PlanItemResponse(
              id = rawToUuid(r.get(idF)!!).toString(),
              contentId = rawToUuid(r.get(contentIdF)!!).toString(),
              dayOfWeek = (r.get(dayOfWeekF) ?: "MON").uppercase(),
              targetMinutes = r.get(targetMinutesF) ?: 0,
              orderIndex = r.get(orderIndexF) ?: 0,
              allowedStart = r.get(allowedStartF),
              allowedEnd = r.get(allowedEndF),
            )
          }
          .sortedWith(compareBy<PlanItemResponse>({ it.dayOfWeek }, { it.orderIndex }))

        call.respond(
          PlanResponse(
            id = rawToUuid(planRaw).toString(),
            childId = childUuid.toString(),
            weekStart = weekStart.toString(),
            dailyTimeLimitMin = plan.get(dailyLimitF) ?: 45,
            breakAfterMin = plan.get(breakAfterF) ?: 20,
            breakDurationMin = plan.get(breakDurationF) ?: 5,
            status = plan.get(statusF) ?: "ACTIVE",
            items = items,
          ),
        )
        return@get
      }

      val plan = PlansInMemory.getOrCreate(childId, weekStart)
      call.respond(
        PlanResponse(
          id = plan.id,
          childId = plan.childId,
          weekStart = plan.weekStart.toString(),
          dailyTimeLimitMin = plan.dailyTimeLimitMin,
          breakAfterMin = plan.breakAfterMin,
          breakDurationMin = plan.breakDurationMin,
          status = plan.status,
          items = plan.items.sortedWith(compareBy<PlanItemResponse>({ it.dayOfWeek }, { it.orderIndex })),
        ),
      )
    }

    post("/items") {
      if (!canWrite(call)) {
        call.respond(HttpStatusCode.Forbidden, problemDetails("Forbidden", 403, "Insufficient role"))
        return@post
      }

      val childId = call.parameters["childId"].orEmpty()
      if (childId.isBlank()) {
        call.respond(HttpStatusCode.BadRequest, problemDetails("Bad Request", 400, "Missing childId"))
        return@post
      }

      val req = call.receive<CreatePlanItemRequest>()
      if (req.contentId.isBlank() || req.dayOfWeek.isBlank() || req.targetMinutes <= 0) {
        call.respond(HttpStatusCode.BadRequest, problemDetails("Bad Request", 400, "Missing or invalid fields"))
        return@post
      }

      val weekStart = parseWeekStart(call.request.queryParameters["weekStart"])

      val db = call.databaseOrNull()
      if (db != null) {
        val dsl = db.dsl
        val childUuid = try {
          UUID.fromString(childId)
        } catch (_: Exception) {
          call.respond(HttpStatusCode.BadRequest, problemDetails("Bad Request", 400, "Invalid childId"))
          return@post
        }

        val contentUuid = try {
          UUID.fromString(req.contentId)
        } catch (_: Exception) {
          call.respond(HttpStatusCode.BadRequest, problemDetails("Bad Request", 400, "Invalid contentId"))
          return@post
        }

        val childRaw = uuidToRaw(childUuid)
        val contentRaw = uuidToRaw(contentUuid)
        val weekSql = Date.valueOf(weekStart)

        val childProfiles = DSL.table(DSL.name("child_profiles"))
        val learningPlans = DSL.table(DSL.name("learning_plans"))
        val planItems = DSL.table(DSL.name("plan_items"))
        val contentItems = DSL.table(DSL.name("content_items"))

        val idF = DSL.field(DSL.name("id"), ByteArray::class.java)
        val householdIdF = DSL.field(DSL.name("household_id"), ByteArray::class.java)
        val childIdF = DSL.field(DSL.name("child_id"), ByteArray::class.java)
        val nameF = DSL.field(DSL.name("name"), String::class.java)
        val weekStartF = DSL.field(DSL.name("week_start"), Date::class.java)
        val dailyLimitF = DSL.field(DSL.name("daily_time_limit_min"), Int::class.java)
        val breakAfterF = DSL.field(DSL.name("break_after_min"), Int::class.java)
        val breakDurationF = DSL.field(DSL.name("break_duration_min"), Int::class.java)
        val statusF = DSL.field(DSL.name("status"), String::class.java)

        val planIdF = DSL.field(DSL.name("plan_id"), ByteArray::class.java)
        val contentIdF = DSL.field(DSL.name("content_id"), ByteArray::class.java)
        val dayOfWeekF = DSL.field(DSL.name("day_of_week"), String::class.java)
        val targetMinutesF = DSL.field(DSL.name("target_minutes"), Int::class.java)
        val orderIndexF = DSL.field(DSL.name("order_index"), Int::class.java)
        val allowedStartF = DSL.field(DSL.name("allowed_start"), String::class.java)
        val allowedEndF = DSL.field(DSL.name("allowed_end"), String::class.java)

        val hhRaw = dsl.select(householdIdF)
          .from(childProfiles)
          .where(idF.eq(childRaw))
          .fetchOne(householdIdF)

        if (hhRaw == null) {
          call.respond(HttpStatusCode.NotFound, problemDetails("Not Found", 404, "Child not found"))
          return@post
        }

        val contentExists = dsl.fetchExists(
          dsl.selectOne().from(contentItems).where(idF.eq(contentRaw)),
        )
        if (!contentExists) {
          call.respond(HttpStatusCode.BadRequest, problemDetails("Bad Request", 400, "Unknown contentId"))
          return@post
        }

        var planRaw = dsl.select(idF)
          .from(learningPlans)
          .where(childIdF.eq(childRaw))
          .and(weekStartF.eq(weekSql))
          .fetchOne(idF)

        if (planRaw == null) {
          val planUuid = UUID.randomUUID()
          planRaw = uuidToRaw(planUuid)
          dsl.insertInto(learningPlans)
            .set(idF, planRaw)
            .set(householdIdF, hhRaw)
            .set(childIdF, childRaw)
            .set(nameF, "Weekly plan")
            .set(weekStartF, weekSql)
            .set(dailyLimitF, 45)
            .set(breakAfterF, 20)
            .set(breakDurationF, 5)
            .set(statusF, "ACTIVE")
            .execute()
        }

        val day = req.dayOfWeek.uppercase()
        val nextIndex = (dsl.select(DSL.max(orderIndexF))
          .from(planItems)
          .where(planIdF.eq(planRaw))
          .and(dayOfWeekF.eq(day))
          .fetchOne(0, Int::class.java) ?: 0) + 1

        val itemUuid = UUID.randomUUID()
        val itemRaw = uuidToRaw(itemUuid)
        dsl.insertInto(planItems)
          .set(idF, itemRaw)
          .set(planIdF, planRaw)
          .set(contentIdF, contentRaw)
          .set(dayOfWeekF, day)
          .set(targetMinutesF, req.targetMinutes)
          .set(orderIndexF, nextIndex)
          .set(allowedStartF, req.allowedStart)
          .set(allowedEndF, req.allowedEnd)
          .execute()

        call.respond(
          HttpStatusCode.Created,
          PlanItemResponse(
            id = itemUuid.toString(),
            contentId = contentUuid.toString(),
            dayOfWeek = day,
            targetMinutes = req.targetMinutes,
            orderIndex = nextIndex,
            allowedStart = req.allowedStart,
            allowedEnd = req.allowedEnd,
          ),
        )
        return@post
      }

      val plan = PlansInMemory.getOrCreate(childId, weekStart)
      val created = PlansInMemory.addItem(plan, req)
      call.respond(HttpStatusCode.Created, created)
    }
  }
}
