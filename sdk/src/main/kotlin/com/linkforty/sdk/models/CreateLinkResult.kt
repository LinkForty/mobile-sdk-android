package com.linkforty.sdk.models

import com.squareup.moshi.JsonClass

/**
 * Result of creating a short link.
 */
@JsonClass(generateAdapter = true)
data class CreateLinkResult(
    /** Full shareable URL (e.g., "https://go.yourdomain.com/tmpl/abc123") */
    val url: String,

    /** The generated short code */
    val shortCode: String,

    /** Link UUID */
    val linkId: String
)
