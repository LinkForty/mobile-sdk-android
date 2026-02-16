package com.linkforty.sdk.utilities

import android.net.Uri
import com.linkforty.sdk.models.DeepLinkData
import com.linkforty.sdk.models.UTMParameters

/**
 * Utility for parsing URLs and extracting parameters.
 */
internal object UrlParser {

    /**
     * Extracts the short code from a URL path.
     * Short code is typically the last path segment.
     * e.g., "https://go.example.com/abc123" -> "abc123"
     */
    fun extractShortCode(uri: Uri): String? {
        return uri.pathSegments?.lastOrNull()
    }

    /**
     * Extracts UTM parameters from URL query.
     * @return UTM parameters if any are found, null otherwise
     */
    fun extractUTMParameters(uri: Uri): UTMParameters? {
        val source = uri.getQueryParameter("utm_source")
        val medium = uri.getQueryParameter("utm_medium")
        val campaign = uri.getQueryParameter("utm_campaign")
        val term = uri.getQueryParameter("utm_term")
        val content = uri.getQueryParameter("utm_content")

        // Only create UTM parameters if at least one is present
        if (source == null && medium == null && campaign == null && term == null && content == null) {
            return null
        }

        return UTMParameters(
            source = source,
            medium = medium,
            campaign = campaign,
            term = term,
            content = content
        )
    }

    /**
     * Extracts custom (non-UTM) query parameters from URL.
     * @return Map of custom parameters, empty if none found
     */
    fun extractCustomParameters(uri: Uri): Map<String, String> {
        val utmKeys = setOf("utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content")
        val customParams = mutableMapOf<String, String>()

        uri.queryParameterNames?.forEach { name ->
            if (name !in utmKeys) {
                uri.getQueryParameter(name)?.let { value ->
                    customParams[name] = value
                }
            }
        }

        return customParams
    }

    /**
     * Parses a URI into DeepLinkData.
     * @return DeepLinkData with extracted information, null if no short code found
     */
    fun parseDeepLink(uri: Uri): DeepLinkData? {
        val shortCode = extractShortCode(uri) ?: return null
        val utmParameters = extractUTMParameters(uri)
        val customParameters = extractCustomParameters(uri)

        return DeepLinkData(
            shortCode = shortCode,
            androidURL = uri.toString(),
            utmParameters = utmParameters,
            customParameters = customParameters.ifEmpty { null }
        )
    }
}
