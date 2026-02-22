package com.guardiangrow.app.http

import kotlinx.serialization.Serializable

@Serializable
data class ProblemDetails(
  val type: String? = null,
  val title: String,
  val status: Int,
  val detail: String? = null,
  val instance: String? = null,
)

fun problemDetails(
  title: String,
  status: Int,
  detail: String? = null,
  instance: String? = null,
  type: String? = null,
) = ProblemDetails(type = type, title = title, status = status, detail = detail, instance = instance)
