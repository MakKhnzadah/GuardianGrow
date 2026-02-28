package com.guardiangrow.app.routes

import com.guardiangrow.app.db.databaseOrNull
import com.guardiangrow.app.db.rawToUuid
import com.guardiangrow.app.db.uuidToRaw
import com.guardiangrow.app.http.problemDetails
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.jooq.impl.DSL
import java.sql.Date
import java.util.concurrent.atomic.AtomicBoolean

@Serializable
private data class ContentListItemResponse(
  val id: String,
  val type: String,
  val title: String,
  val topic: String? = null,
  val minAge: Int,
  val maxAge: Int,
  val difficulty: Int? = null,
  val estMinutes: Int? = null,
  val status: String,
)

@Serializable
private data class ContentDetailResponse(
  val id: String,
  val type: String,
  val title: String,
  val topic: String? = null,
  val minAge: Int,
  val maxAge: Int,
  val difficulty: Int? = null,
  val estMinutes: Int? = null,
  val status: String,
  val body: Map<String, String>? = null,
)

private data class ContentRecord(
  val item: ContentDetailResponse,
  val createdAt: Instant,
)

private object ContentCatalogInMemory {
  private val byId = ConcurrentHashMap<String, ContentRecord>()

  init {
    // Seed data so the library has something to show in dev.
    seed(
      type = "LESSON",
      title = "Fractions: halves and quarters",
      topic = "math",
      minAge = 6,
      maxAge = 9,
      difficulty = 2,
      estMinutes = 12,
      status = "PUBLISHED",
    )
    seed(
      type = "STORY",
      title = "The Curious Comet",
      topic = "science",
      minAge = 7,
      maxAge = 11,
      difficulty = 1,
      estMinutes = 8,
      status = "PUBLISHED",
    )
    seed(
      type = "PUZZLE",
      title = "Pattern Match: Shapes",
      topic = "logic",
      minAge = 5,
      maxAge = 8,
      difficulty = 3,
      estMinutes = 10,
      status = "PUBLISHED",
    )
  }

  private fun seed(
    type: String,
    title: String,
    topic: String,
    minAge: Int,
    maxAge: Int,
    difficulty: Int,
    estMinutes: Int,
    status: String,
  ) {
    val id = UUID.randomUUID().toString()
    val detail = ContentDetailResponse(
      id = id,
      type = type,
      title = title,
      topic = topic,
      minAge = minAge,
      maxAge = maxAge,
      difficulty = difficulty,
      estMinutes = estMinutes,
      status = status,
      body = mapOf("text" to "Preview body for: $title"),
    )
    byId[id] = ContentRecord(detail, Instant.now())
  }

  fun get(id: String): ContentRecord? = byId[id]

  fun list(): List<ContentRecord> = byId.values.toList()
}

private object ContentCatalogDbSeeder {
  private val didAttempt = AtomicBoolean(false)

  fun ensureSeeded(call: ApplicationCall) {
    val db = call.databaseOrNull() ?: return
    val dsl = db.dsl
    if (didAttempt.get()) return

    val contentItems = DSL.table(DSL.name("content_items"))
    val contentVersions = DSL.table(DSL.name("content_versions"))

    val idF = DSL.field(DSL.name("id"), ByteArray::class.java)
    val typeF = DSL.field(DSL.name("type"), String::class.java)
    val titleF = DSL.field(DSL.name("title"), String::class.java)
    val topicF = DSL.field(DSL.name("topic"), String::class.java)
    val minAgeF = DSL.field(DSL.name("min_age"), Int::class.java)
    val maxAgeF = DSL.field(DSL.name("max_age"), Int::class.java)
    val difficultyF = DSL.field(DSL.name("difficulty"), Int::class.java)
    val estMinutesF = DSL.field(DSL.name("est_minutes"), Int::class.java)
    val statusF = DSL.field(DSL.name("status"), String::class.java)
    val publishedVersionIdF = DSL.field(DSL.name("published_version_id"), ByteArray::class.java)

    val cvIdF = DSL.field(DSL.name("id"), ByteArray::class.java)
    val cvContentIdF = DSL.field(DSL.name("content_id"), ByteArray::class.java)
    val cvVersionNoF = DSL.field(DSL.name("version_no"), Int::class.java)
    val cvBodyJsonF = DSL.field(DSL.name("body_json"), String::class.java)
    val cvStatusF = DSL.field(DSL.name("status"), String::class.java)

    try {
      val existing = dsl.fetchCount(contentItems)
      if (existing > 0) {
        didAttempt.set(true)
        return
      }

      fun seedOne(
        type: String,
        title: String,
        topic: String,
        minAge: Int,
        maxAge: Int,
        difficulty: Int,
        estMinutes: Int,
      ) {
        val contentUuid = UUID.randomUUID()
        val versionUuid = UUID.randomUUID()
        val contentRaw = uuidToRaw(contentUuid)
        val versionRaw = uuidToRaw(versionUuid)
        val bodyJson = "{\"text\":\"Preview body for: ${title.replace("\"", "\\\"")}\"}"

        dsl.insertInto(contentItems)
          .set(idF, contentRaw)
          .set(typeF, type)
          .set(titleF, title)
          .set(topicF, topic)
          .set(minAgeF, minAge)
          .set(maxAgeF, maxAge)
          .set(difficultyF, difficulty)
          .set(estMinutesF, estMinutes)
          .set(statusF, "PUBLISHED")
          .set(publishedVersionIdF, versionRaw)
          .execute()

        dsl.insertInto(contentVersions)
          .set(cvIdF, versionRaw)
          .set(cvContentIdF, contentRaw)
          .set(cvVersionNoF, 1)
          .set(cvBodyJsonF, bodyJson)
          .set(cvStatusF, "PUBLISHED")
          .execute()
      }

      seedOne(
        type = "LESSON",
        title = "Fractions: halves and quarters",
        topic = "math",
        minAge = 6,
        maxAge = 9,
        difficulty = 2,
        estMinutes = 12,
      )
      seedOne(
        type = "STORY",
        title = "The Curious Comet",
        topic = "science",
        minAge = 7,
        maxAge = 11,
        difficulty = 1,
        estMinutes = 8,
      )
      seedOne(
        type = "PUZZLE",
        title = "Pattern Match: Shapes",
        topic = "logic",
        minAge = 5,
        maxAge = 8,
        difficulty = 3,
        estMinutes = 10,
      )
    } finally {
      didAttempt.set(true)
    }
  }
}

internal fun lookupContentTitle(contentId: String): String? = ContentCatalogInMemory.get(contentId)?.item?.title

fun Route.contentRoutes() {
  route("/content") {
    fun requireParentOrAdmin(call: ApplicationCall): Boolean {
      val principal = call.principal<JWTPrincipal>() ?: return false
      val role = principal.payload.getClaim("role").asString()
      return role == "PARENT" || role == "ADMIN"
    }

    get {
      if (!requireParentOrAdmin(call)) {
        call.respond(HttpStatusCode.Forbidden, problemDetails("Forbidden", 403, "Insufficient role"))
        return@get
      }

      val db = call.databaseOrNull()
      if (db != null) {
        ContentCatalogDbSeeder.ensureSeeded(call)

        val dsl = db.dsl
        val table = DSL.table(DSL.name("content_items"))
        val idF = DSL.field(DSL.name("id"), ByteArray::class.java)
        val typeF = DSL.field(DSL.name("type"), String::class.java)
        val titleF = DSL.field(DSL.name("title"), String::class.java)
        val topicF = DSL.field(DSL.name("topic"), String::class.java)
        val minAgeF = DSL.field(DSL.name("min_age"), Int::class.java)
        val maxAgeF = DSL.field(DSL.name("max_age"), Int::class.java)
        val difficultyF = DSL.field(DSL.name("difficulty"), Int::class.java)
        val estMinutesF = DSL.field(DSL.name("est_minutes"), Int::class.java)
        val statusF = DSL.field(DSL.name("status"), String::class.java)
        val createdAtF = DSL.field(DSL.name("created_at"))

        val q = call.request.queryParameters["q"]?.trim().orEmpty().ifBlank { null }
        val topic = call.request.queryParameters["topic"]?.trim().orEmpty().ifBlank { null }
        val type = call.request.queryParameters["type"]?.trim().orEmpty().ifBlank { null }
        val age = call.request.queryParameters["age"]?.toIntOrNull()
        val difficulty = call.request.queryParameters["difficulty"]?.toIntOrNull()
        val minDuration = call.request.queryParameters["minDuration"]?.toIntOrNull()
        val maxDuration = call.request.queryParameters["maxDuration"]?.toIntOrNull()
        val page = (call.request.queryParameters["page"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
        val pageSize = (call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 50).coerceIn(1, 200)
        val sort = call.request.queryParameters["sort"]?.trim().orEmpty()

        val ordered = when (sort.lowercase()) {
          "createdat,desc" -> dsl.select(idF, typeF, titleF, topicF, minAgeF, maxAgeF, difficultyF, estMinutesF, statusF)
            .from(table)
            .orderBy(createdAtF.desc())
            .fetch()
          "createdat,asc" -> dsl.select(idF, typeF, titleF, topicF, minAgeF, maxAgeF, difficultyF, estMinutesF, statusF)
            .from(table)
            .orderBy(createdAtF.asc())
            .fetch()
          else -> dsl.select(idF, typeF, titleF, topicF, minAgeF, maxAgeF, difficultyF, estMinutesF, statusF)
            .from(table)
            .orderBy(titleF.asc())
            .fetch()
        }

        var items = ordered.map { r ->
          ContentListItemResponse(
            id = rawToUuid(r.get(idF)!!).toString(),
            type = r.get(typeF) ?: "",
            title = r.get(titleF) ?: "",
            topic = r.get(topicF),
            minAge = r.get(minAgeF) ?: 0,
            maxAge = r.get(maxAgeF) ?: 0,
            difficulty = r.get(difficultyF),
            estMinutes = r.get(estMinutesF),
            status = r.get(statusF) ?: "PUBLISHED",
          )
        }

        if (type != null) items = items.filter { it.type.equals(type, ignoreCase = true) }
        if (topic != null) items = items.filter { (it.topic ?: "").equals(topic, ignoreCase = true) }
        if (difficulty != null) items = items.filter { it.difficulty == difficulty }
        if (minDuration != null) items = items.filter { (it.estMinutes ?: 0) >= minDuration }
        if (maxDuration != null) items = items.filter { (it.estMinutes ?: 0) <= maxDuration }
        if (age != null) items = items.filter { age >= it.minAge && age <= it.maxAge }
        if (q != null) {
          val needle = q.lowercase()
          items = items.filter { it.title.lowercase().contains(needle) || (it.topic?.lowercase()?.contains(needle) ?: false) }
        }

        val offset = (page - 1) * pageSize
        call.respond(items.drop(offset).take(pageSize))
        return@get
      }

      val q = call.request.queryParameters["q"]?.trim().orEmpty().ifBlank { null }
      val topic = call.request.queryParameters["topic"]?.trim().orEmpty().ifBlank { null }
      val type = call.request.queryParameters["type"]?.trim().orEmpty().ifBlank { null }
      val age = call.request.queryParameters["age"]?.toIntOrNull()
      val difficulty = call.request.queryParameters["difficulty"]?.toIntOrNull()
      val minDuration = call.request.queryParameters["minDuration"]?.toIntOrNull()
      val maxDuration = call.request.queryParameters["maxDuration"]?.toIntOrNull()
      val page = (call.request.queryParameters["page"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
      val pageSize = (call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 50).coerceIn(1, 200)
      val sort = call.request.queryParameters["sort"]?.trim().orEmpty()

      var results = ContentCatalogInMemory.list()

      if (type != null) {
        results = results.filter { it.item.type.equals(type, ignoreCase = true) }
      }
      if (topic != null) {
        results = results.filter { (it.item.topic ?: "").equals(topic, ignoreCase = true) }
      }
      if (difficulty != null) {
        results = results.filter { it.item.difficulty == difficulty }
      }
      if (minDuration != null) {
        results = results.filter { (it.item.estMinutes ?: 0) >= minDuration }
      }
      if (maxDuration != null) {
        results = results.filter { (it.item.estMinutes ?: 0) <= maxDuration }
      }
      if (age != null) {
        results = results.filter { age >= it.item.minAge && age <= it.item.maxAge }
      }
      if (q != null) {
        val needle = q.lowercase()
        results = results.filter {
          it.item.title.lowercase().contains(needle) || (it.item.topic?.lowercase()?.contains(needle) ?: false)
        }
      }

      results = when (sort.lowercase()) {
        "createdat,desc" -> results.sortedByDescending { it.createdAt }
        "createdat,asc" -> results.sortedBy { it.createdAt }
        else -> results.sortedBy { it.item.title }
      }

      val offset = (page - 1) * pageSize
      val pageItems = results.drop(offset).take(pageSize)

      call.respond(
        pageItems.map {
          ContentListItemResponse(
            id = it.item.id,
            type = it.item.type,
            title = it.item.title,
            topic = it.item.topic,
            minAge = it.item.minAge,
            maxAge = it.item.maxAge,
            difficulty = it.item.difficulty,
            estMinutes = it.item.estMinutes,
            status = it.item.status,
          )
        },
      )
    }

    get("/{contentId}") {
      if (!requireParentOrAdmin(call)) {
        call.respond(HttpStatusCode.Forbidden, problemDetails("Forbidden", 403, "Insufficient role"))
        return@get
      }

      val contentId = call.parameters["contentId"].orEmpty()
      if (contentId.isBlank()) {
        call.respond(HttpStatusCode.BadRequest, problemDetails("Bad Request", 400, "Missing contentId"))
        return@get
      }

      val db = call.databaseOrNull()
      if (db != null) {
        ContentCatalogDbSeeder.ensureSeeded(call)
        val dsl = db.dsl
        val table = DSL.table(DSL.name("content_items"))
        val idF = DSL.field(DSL.name("id"), ByteArray::class.java)
        val typeF = DSL.field(DSL.name("type"), String::class.java)
        val titleF = DSL.field(DSL.name("title"), String::class.java)
        val topicF = DSL.field(DSL.name("topic"), String::class.java)
        val minAgeF = DSL.field(DSL.name("min_age"), Int::class.java)
        val maxAgeF = DSL.field(DSL.name("max_age"), Int::class.java)
        val difficultyF = DSL.field(DSL.name("difficulty"), Int::class.java)
        val estMinutesF = DSL.field(DSL.name("est_minutes"), Int::class.java)
        val statusF = DSL.field(DSL.name("status"), String::class.java)

        val contentUuid = try {
          UUID.fromString(contentId)
        } catch (_: Exception) {
          call.respond(HttpStatusCode.BadRequest, problemDetails("Bad Request", 400, "Invalid contentId"))
          return@get
        }

        val row = dsl
          .select(idF, typeF, titleF, topicF, minAgeF, maxAgeF, difficultyF, estMinutesF, statusF)
          .from(table)
          .where(idF.eq(uuidToRaw(contentUuid)))
          .fetchOne()

        if (row == null) {
          call.respond(HttpStatusCode.NotFound, problemDetails("Not Found", 404, "Content not found"))
          return@get
        }

        val title = row.get(titleF) ?: ""
        call.respond(
          ContentDetailResponse(
            id = contentUuid.toString(),
            type = row.get(typeF) ?: "",
            title = title,
            topic = row.get(topicF),
            minAge = row.get(minAgeF) ?: 0,
            maxAge = row.get(maxAgeF) ?: 0,
            difficulty = row.get(difficultyF),
            estMinutes = row.get(estMinutesF),
            status = row.get(statusF) ?: "PUBLISHED",
            body = mapOf("text" to "Preview body for: $title"),
          ),
        )
        return@get
      }

      val record = ContentCatalogInMemory.get(contentId)
      if (record == null) {
        call.respond(HttpStatusCode.NotFound, problemDetails("Not Found", 404, "Content not found"))
        return@get
      }

      call.respond(record.item)
    }
  }
}
