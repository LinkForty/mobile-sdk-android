package com.linkforty.sdk.models

import com.squareup.moshi.JsonClass

/**
 * Response from the event tracking endpoint, `{"eventId": "...", "acknowledged": true}`.
 *
 * Every field is optional: a 2xx response means the server stored the event,
 * whatever the body carries.
 */
@JsonClass(generateAdapter = true)
data class EventResponse(
    /** ID of the stored event */
    val eventId: String? = null,

    /** Whether the server accepted the event */
    val acknowledged: Boolean? = null,

    /** Sent by older servers in place of [acknowledged] */
    val success: Boolean? = null
)
