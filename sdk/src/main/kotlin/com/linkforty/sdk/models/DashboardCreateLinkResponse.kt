package com.linkforty.sdk.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Response from the dashboard link creation endpoint (POST /api/links).
 * Maps the snake_case response to our internal model.
 */
@JsonClass(generateAdapter = true)
internal data class DashboardCreateLinkResponse(
    val id: String,
    @Json(name = "short_code") val shortCode: String
)
