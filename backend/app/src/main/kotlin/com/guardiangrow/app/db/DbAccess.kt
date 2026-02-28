package com.guardiangrow.app.db

import com.guardiangrow.data.Database
import io.ktor.server.application.*
import io.ktor.util.*
import java.nio.ByteBuffer
import java.util.UUID

private val DatabaseKey = AttributeKey<Database>("database")

fun Application.installDatabase(database: Database) {
  attributes.put(DatabaseKey, database)
}

fun Application.databaseOrNull(): Database? {
  return if (attributes.contains(DatabaseKey)) attributes[DatabaseKey] else null
}

fun ApplicationCall.databaseOrNull(): Database? = application.databaseOrNull()

fun uuidToRaw(uuid: UUID): ByteArray {
  val buf = ByteBuffer.allocate(16)
  buf.putLong(uuid.mostSignificantBits)
  buf.putLong(uuid.leastSignificantBits)
  return buf.array()
}

fun rawToUuid(raw: ByteArray): UUID {
  val buf = ByteBuffer.wrap(raw)
  val msb = buf.long
  val lsb = buf.long
  return UUID(msb, lsb)
}
