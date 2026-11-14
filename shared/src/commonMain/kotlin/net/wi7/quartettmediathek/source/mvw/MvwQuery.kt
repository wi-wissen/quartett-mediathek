package net.wi7.quartettmediathek.source.mvw

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class MvwField(val wire: String) { CHANNEL("channel"), TOPIC("topic"), TITLE("title"), DESCRIPTION("description") }

enum class MvwSortBy(val wire: String) { TIMESTAMP("timestamp"), DURATION("duration"), CHANNEL("channel") }

enum class MvwSortOrder(val wire: String) { ASC("asc"), DESC("desc") }

/** One entry of the `queries` array: [query] is matched against any of [fields] (OR across fields). */
data class MvwClause(val fields: List<MvwField>, val query: String) {
    constructor(field: MvwField, query: String) : this(listOf(field), query)
}

/** A MediathekViewWeb request. Several clauses are AND-combined. */
data class MvwQuery(
    val clauses: List<MvwClause>,
    val sortBy: MvwSortBy = MvwSortBy.TIMESTAMP,
    val sortOrder: MvwSortOrder = MvwSortOrder.DESC,
    val offset: Int = 0,
    val size: Int = 50,
    val durationMinSeconds: Int? = null,
    val durationMaxSeconds: Int? = null,
    val future: Boolean = false,
) {
    /** The request body (sent as `text/plain`). */
    fun toJson(): String = toJsonObject().toString()

    fun toJsonObject(): JsonObject = buildJsonObject {
        put(
            "queries",
            JsonArray(
                clauses.map { c ->
                    buildJsonObject {
                        put("fields", JsonArray(c.fields.map { JsonPrimitive(it.wire) }))
                        put("query", c.query)
                    }
                },
            ),
        )
        put("sortBy", sortBy.wire)
        put("sortOrder", sortOrder.wire)
        put("future", future)
        put("offset", offset)
        put("size", size)
        durationMinSeconds?.let { put("duration_min", it) }
        durationMaxSeconds?.let { put("duration_max", it) }
    }
}
