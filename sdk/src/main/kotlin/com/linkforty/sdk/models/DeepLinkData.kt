package com.linkforty.sdk.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
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
