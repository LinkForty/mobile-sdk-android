package com.linkforty.sdk.models

import com.linkforty.sdk.LinkFortyLogger
import com.squareup.moshi.FromJson
import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
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
 * Moshi adapter that decodes an unusable [DeepLinkData] payload as null instead
 * of throwing.
 *
 * Organic (unattributed) installs come back as `deepLinkData: {}` rather than
 * `null`, and an object without a `shortCode` carries no link to route to.
 * Without this, decoding an install response for an organic install fails
 * outright — taking SDK initialization down with it — over a field the caller
 * never needed.
 *
 * Only registered on the SDK's network [com.squareup.moshi.Moshi] instance;
 * locally stored deep link data is written by the SDK and stays strict.
 */
internal class LenientDeepLinkDataAdapter {

    @FromJson
    fun fromJson(reader: JsonReader, delegate: JsonAdapter<DeepLinkData>): DeepLinkData? {
        val value = reader.readJsonValue()
        if (value !is Map<*, *>) return null

        return try {
            delegate.fromJsonValue(value)
        } catch (e: JsonDataException) {
            LinkFortyLogger.log("Ignoring undecodable deepLinkData: ${e.message}")
            null
        }
    }

    @ToJson
    fun toJson(writer: JsonWriter, value: DeepLinkData?, delegate: JsonAdapter<DeepLinkData>) {
        if (value == null) writer.nullValue() else delegate.toJson(writer, value)
    }
}
