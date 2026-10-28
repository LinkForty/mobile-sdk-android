package com.linkforty.sdk.models

import com.linkforty.sdk.errors.LinkFortyError
import java.net.URI

/**
 * Configuration for the LinkForty SDK.
 *
 * @property baseURL The LinkForty server the SDK talks to: `https://api.linkforty.com`
 *   on the hosted service, or your own server's URL. Must be HTTPS (HTTP is allowed
 *   for localhost, 127.0.0.1, 0.0.0.0 and 10.0.2.2).
 * @property apiKey API key, needed only for [com.linkforty.sdk.LinkForty.createLink].
 *   When set it is sent as a Bearer token on every request. It is a secret: leave it
 *   out unless the app creates links.
 * @property appToken Your workspace's public app token. Required on the hosted service:
 *   it matches installs only to your workspace's links, puts organic installs (Play
 *   Store discovery, social mentions, etc.) in your workspace, and lets the SDK learn
 *   your link domains. A self-hosted Core server doesn't need it. Safe to ship in your
 *   app: it identifies the workspace only and can't authenticate API actions.
 *   Format: `at_<32 hex chars>`.
 * @property debug Enable debug logging (default: false)
 * @property attributionWindowHours Attribution window in hours (default: 168 = 7 days)
 */
data class LinkFortyConfig(
    val baseURL: String,
    val apiKey: String? = null,
    val appToken: String? = null,
    val debug: Boolean = false,
    val attributionWindowHours: Int = 168
) {
    /**
     * Validates the configuration.
     * @throws LinkFortyError.InvalidConfiguration if validation fails
     */
    fun validate() {
        // Validate HTTPS (except localhost / 127.0.0.1 / 10.0.2.2)
        val uri = try {
            URI(baseURL)
        } catch (e: Exception) {
            throw LinkFortyError.InvalidConfiguration("Invalid base URL: $baseURL")
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && !isLocalhost(uri)) {
            throw LinkFortyError.InvalidConfiguration(
                "Base URL must use HTTPS (HTTP only allowed for localhost)"
            )
        }

        // Validate attribution window (1 hour to 90 days)
        if (attributionWindowHours < 1 || attributionWindowHours > 2160) {
            throw LinkFortyError.InvalidConfiguration(
                "Attribution window must be between 1 and 2160 hours"
            )
        }
    }

    private fun isLocalhost(uri: URI): Boolean {
        val host = uri.host ?: return false
        return host == "localhost" ||
            host == "127.0.0.1" ||
            host == "0.0.0.0" ||
            host == "10.0.2.2"
    }

    override fun toString(): String = buildString {
        append("LinkFortyConfig(")
        append("baseURL=$baseURL, ")
        append("apiKey=${if (apiKey != null) "***" else "null"}, ")
        append("appToken=${if (appToken != null) "***" else "null"}, ")
        append("debug=$debug, ")
        append("attributionWindowHours=$attributionWindowHours")
        append(")")
    }
}
