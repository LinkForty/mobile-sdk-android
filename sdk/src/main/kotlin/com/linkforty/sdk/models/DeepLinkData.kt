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
