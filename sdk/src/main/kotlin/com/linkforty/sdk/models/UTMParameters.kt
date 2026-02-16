package com.linkforty.sdk.models

import com.squareup.moshi.JsonClass

/**
 * UTM parameters for campaign tracking.
 */
@JsonClass(generateAdapter = true)
data class UTMParameters(
    /** Campaign source (e.g., "google", "facebook", "email") */
    val source: String? = null,
    /** Campaign medium (e.g., "cpc", "banner", "email") */
    val medium: String? = null,
    /** Campaign name (e.g., "summer_sale", "product_launch") */
    val campaign: String? = null,
    /** Campaign term (e.g., "running+shoes") */
    val term: String? = null,
    /** Campaign content (e.g., "logolink", "textlink") */
    val content: String? = null
) {
    /** Checks if any UTM parameter is set. */
    val hasAnyParameter: Boolean
        get() = source != null || medium != null || campaign != null || term != null || content != null
}
