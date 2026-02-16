package com.linkforty.sdk.models

import com.squareup.moshi.JsonClass

/**
 * Options for creating a short link.
 */
@JsonClass(generateAdapter = true)
data class CreateLinkOptions(
    /** Template ID (auto-selected if omitted) */
    val templateId: String? = null,

    /** Template slug (only needed with templateId) */
    val templateSlug: String? = null,

    /** Deep link parameters for in-app routing (e.g., mapOf("route" to "VIDEO_VIEWER")) */
    val deepLinkParameters: Map<String, String>? = null,

    /** Link title */
    val title: String? = null,

    /** Link description */
    val description: String? = null,

    /** Custom short code (auto-generated if omitted) */
    val customCode: String? = null,

    /** UTM parameters for campaign tracking */
    val utmParameters: UTMParameters? = null
)
