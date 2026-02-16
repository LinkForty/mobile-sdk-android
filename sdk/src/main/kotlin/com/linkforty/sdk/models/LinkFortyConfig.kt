package com.linkforty.sdk.models

import com.linkforty.sdk.errors.LinkFortyError
import java.net.URI

/**
 * Configuration for the LinkForty SDK.
 *
 * @property baseURL The base URL of your LinkForty instance (e.g., "https://go.yourdomain.com")
 * @property apiKey Optional API key for LinkForty Cloud authentication
 * @property debug Enable debug logging (default: false)
 * @property attributionWindowHours Attribution window in hours (default: 168 = 7 days)
 */
data class LinkFortyConfig(
    val baseURL: String,
    val apiKey: String? = null,
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
        append("debug=$debug, ")
        append("attributionWindowHours=$attributionWindowHours")
        append(")")
    }
}
