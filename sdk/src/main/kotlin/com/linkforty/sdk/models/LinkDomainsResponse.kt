package com.linkforty.sdk.models

import com.squareup.moshi.JsonClass

/** The hosts the workspace's links are served from, as the server reports them. */
@JsonClass(generateAdapter = true)
internal data class LinkDomainsResponse(
    val domains: List<String> = emptyList()
)
