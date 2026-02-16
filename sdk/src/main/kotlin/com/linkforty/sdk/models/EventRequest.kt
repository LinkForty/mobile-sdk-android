package com.linkforty.sdk.models

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonClass
import com.squareup.moshi.ToJson
import java.time.Instant

/**
 * Request payload for tracking events.
 */
@JsonClass(generateAdapter = true)
data class EventRequest(
    /** The install ID from attribution */
    val installId: String,

    /** Name of the event (e.g., "purchase", "signup") */
    val eventName: String,

    /** Custom event properties (must be JSON-serializable) */
    val eventData: Map<String, @JvmSuppressWildcards Any>,

    /** ISO 8601 timestamp of when the event occurred */
    val timestamp: String = Instant.now().toString()
)

/**
 * Moshi adapter for handling `Map<String, Any>` serialization.
 * Equivalent to iOS's `AnyCodable` — handles String, Number, Boolean, List, Map, and null.
 */
internal class AnyJsonAdapter {

    @FromJson
    fun fromJson(value: Any): Any = value

    @ToJson
    fun toJson(value: @JvmSuppressWildcards Any): Any = toJsonValue(value)!!

    private fun toJsonValue(value: Any?): Any? {
        return when (value) {
            null -> null
            is String -> value
            is Number -> value
            is Boolean -> value
            is List<*> -> value.map { toJsonValue(it) }
            is Map<*, *> -> value.entries.associate { (k, v) -> k.toString() to toJsonValue(v) }
            else -> value.toString()
        }
    }
}
