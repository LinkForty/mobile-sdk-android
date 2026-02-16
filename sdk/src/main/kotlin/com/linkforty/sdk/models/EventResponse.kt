package com.linkforty.sdk.models

import com.squareup.moshi.JsonClass

/**
 * Response from the event tracking endpoint.
 */
@JsonClass(generateAdapter = true)
data class EventResponse(
    /** Whether the event was successfully tracked */
    val success: Boolean
)
