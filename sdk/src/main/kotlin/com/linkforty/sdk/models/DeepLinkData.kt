package com.linkforty.sdk.models

import com.linkforty.sdk.LinkFortyLogger
import com.squareup.moshi.FromJson
import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Deep link data returned from attribution or direct deep links.
 */
@JsonClass(generateAdapter = true)
data class DeepLinkData(
    /** The short code of the link (e.g., "abc123") */
    val shortCode: String,

    /** iOS-specific URL (Universal Link or custom scheme) */
    @Json(name = "iosUrl") val iosURL: String? = null,

    /** Android-specific URL (App Link or custom scheme) */
    @Json(name = "androidUrl") val androidURL: String? = null,

    /** Web fallback URL */
    @Json(name = "webUrl") val webURL: String? = null,

    /** UTM parameters from the link */
    val utmParameters: UTMParameters? = null,

    /** Custom query parameters from the link */
    val customParameters: Map<String, String>? = null,

    /** Deep link path for in-app routing (e.g., "/product/123") */
    val deepLinkPath: String? = null,

    /** App URI scheme (e.g., "myapp") */
    val appScheme: String? = null,

    /** When the link was clicked (ISO 8601 string) */
    val clickedAt: String? = null,

    /** The link ID from the backend */
    val linkId: String? = null
) {
    /**
     * Parses [clickedAt] as an [Instant], or null if absent or unparseable.
     */
    fun clickedAtDate(): Instant? {
        return clickedAt?.let {
            try {
                Instant.parse(it)
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }
}

/**
 * Returns a copy with the parameters carried on the opened URL merged in.
 *
 * Resolving a short code returns the link's *stored* configuration; the server
 * has no way to know what was appended to the URL that was actually tapped. The
 * SDK does, having just parsed it. Without this a link shared as
 * `?slug=titanic` reaches the app with that value missing on a direct open,
 * while the same link after a deferred install carries it — the server merges
 * the click's parameters there.
 *
 * URL values win on a key collision, matching that server-side precedence: what
 * a sharer put on the URL is more specific than the link's stored setup.
 *
 * Only [customParameters] is merged. [linkId], [deepLinkPath], [appScheme], the
 * store URLs and [utmParameters] are server truth that a local parse cannot know
 * and must not overwrite.
 */
fun DeepLinkData.mergingUrlParameters(fromUrl: Map<String, String>?): DeepLinkData {
    if (fromUrl.isNullOrEmpty()) return this
    return copy(customParameters = (customParameters ?: emptyMap()) + fromUrl)
}

/**
 * Moshi adapter that decodes an unusable [DeepLinkData] payload as null instead
 * of throwing.
 *
 * Organic (unattributed) installs come back as `deepLinkData: {}` rather than
 * `null`, and an object without a `shortCode` carries no link to route to.
 * Without this, decoding an install response for an organic install fails
 * outright — taking SDK initialization down with it — over a field the caller
 * never needed.
 *
 * A link's parameters can hold any JSON value, while [DeepLinkData.customParameters]
 * is a map of strings: numbers and booleans arrive as their text (`42`, `true`),
 * objects and arrays as JSON.
 *
 * Only registered on the SDK's network [com.squareup.moshi.Moshi] instance;
 * locally stored deep link data is written by the SDK and stays strict.
 */
internal class LenientDeepLinkDataAdapter {

    private val anyAdapter = Moshi.Builder().build().adapter(Any::class.java)

    @FromJson
    fun fromJson(reader: JsonReader, delegate: JsonAdapter<DeepLinkData>): DeepLinkData? {
        val value = reader.readJsonValue()
        if (value !is Map<*, *>) return null

        val parameters = value["customParameters"]
        val normalized = if (parameters is Map<*, *>) {
            val text = parameters.mapValues { (_, v) -> parameterText(v) }.filterValues { it != null }
            value.mapKeys { (k, _) -> k.toString() } + ("customParameters" to text)
        } else {
            value
        }

        return try {
            delegate.fromJsonValue(normalized)
        } catch (e: JsonDataException) {
            LinkFortyLogger.log("Ignoring undecodable deepLinkData: ${e.message}")
            null
        }
    }

    private fun parameterText(value: Any?): String? = when (value) {
        null -> null
        is String -> value
        // JSON numbers decode as Double; 42 reads as "42", not "42.0".
        is Double -> if (value % 1.0 == 0.0 && value in -9.0E15..9.0E15) value.toLong().toString() else value.toString()
        is Boolean -> value.toString()
        else -> anyAdapter.toJson(value)
    }

    @ToJson
    fun toJson(writer: JsonWriter, value: DeepLinkData?, delegate: JsonAdapter<DeepLinkData>) {
        if (value == null) writer.nullValue() else delegate.toJson(writer, value)
    }
}
