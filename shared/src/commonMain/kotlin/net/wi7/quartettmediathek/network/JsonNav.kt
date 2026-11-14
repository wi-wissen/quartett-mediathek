package net.wi7.quartettmediathek.network

import kotlin.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** Null-safe navigation helpers for irregular JSON: `root["a"]["b"][0].string`. */
operator fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)

operator fun JsonElement?.get(index: Int): JsonElement? = (this as? JsonArray)?.getOrNull(index)

/** Follows a chain of object keys. */
fun JsonElement?.path(vararg keys: String): JsonElement? = keys.fold(this) { node, key -> node[key] }

/** Text content of a primitive, or null for missing values, JSON null and non-primitives. */
val JsonElement?.string: String? get() = (this as? JsonPrimitive)?.contentOrNull

/** Like [string] but blank strings count as missing. */
val JsonElement?.nonBlank: String? get() = string?.takeIf { it.isNotBlank() }

val JsonElement?.int: Int? get() = (this as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toInt() }

val JsonElement?.long: Long? get() = (this as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toLong() }

val JsonElement?.bool: Boolean? get() = (this as? JsonPrimitive)?.booleanOrNull

/** Elements of an array, empty for anything else. */
val JsonElement?.list: List<JsonElement> get() = (this as? JsonArray) ?: emptyList()

/** Parses an ISO-8601 instant (with `Z` or an offset), or returns null. */
fun parseInstantOrNull(value: String?): Instant? =
    value?.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it) }.getOrNull() }
