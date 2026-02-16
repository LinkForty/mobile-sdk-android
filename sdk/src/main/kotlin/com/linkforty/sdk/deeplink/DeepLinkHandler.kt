package com.linkforty.sdk.deeplink

import android.net.Uri
import com.linkforty.sdk.LinkFortyLogger
import com.linkforty.sdk.fingerprint.FingerprintCollectorProtocol
import com.linkforty.sdk.models.DeepLinkData
import com.linkforty.sdk.network.HttpMethod
import com.linkforty.sdk.network.NetworkManagerProtocol
import com.linkforty.sdk.network.request
import com.linkforty.sdk.utilities.UrlParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/**
 * Callback for deferred deep links (install attribution).
 * Parameter: Deep link data if attributed, null for organic installs.
 */
typealias DeferredDeepLinkCallback = (DeepLinkData?) -> Unit

/**
 * Callback for direct deep links (App Links, custom schemes).
 * Parameters: the URI that opened the app, and parsed deep link data.
 */
typealias DeepLinkCallback = (Uri, DeepLinkData?) -> Unit

/**
 * Handles deep linking and callbacks.
 */
internal class DeepLinkHandler {

    private val scope = CoroutineScope(Dispatchers.Default)

    private val deferredDeepLinkCallbacks = mutableListOf<DeferredDeepLinkCallback>()
    private val deepLinkCallbacks = mutableListOf<DeepLinkCallback>()

    /** Network manager for server-side URL resolution */
    private var networkManager: NetworkManagerProtocol? = null

    /** Fingerprint collector for resolution requests */
    private var fingerprintCollector: FingerprintCollectorProtocol? = null

    /** Base URL for detecting LinkForty URLs */
    private var baseURL: String? = null

    /** Flag to track if deferred deep link has been delivered */
    private var deferredDeepLinkDelivered = false

    /** Cached deferred deep link data */
    private var cachedDeferredDeepLink: DeepLinkData? = null

    /**
     * Configures the handler with network capabilities for server-side resolution.
     */
    fun configure(
        networkManager: NetworkManagerProtocol,
        fingerprintCollector: FingerprintCollectorProtocol,
        baseURL: String
    ) {
        this.networkManager = networkManager
        this.fingerprintCollector = fingerprintCollector
        this.baseURL = baseURL
    }

    // -- Deferred Deep Link (Install Attribution) --

    /**
     * Registers a callback for deferred deep links.
     * If data is already cached, the callback is invoked immediately on the main thread.
     */
    fun onDeferredDeepLink(callback: DeferredDeepLinkCallback) {
        synchronized(this) {
            deferredDeepLinkCallbacks.add(callback)

            if (deferredDeepLinkDelivered) {
                val data = cachedDeferredDeepLink
                scope.launch {
                    withContext(Dispatchers.Main) {
                        callback(data)
                    }
                }
            }
        }
    }

    /**
     * Delivers deferred deep link data to all registered callbacks.
     */
    fun deliverDeferredDeepLink(deepLinkData: DeepLinkData?) {
        scope.launch {
            val callbacks = synchronized(this@DeepLinkHandler) {
                cachedDeferredDeepLink = deepLinkData
                deferredDeepLinkDelivered = true

                LinkFortyLogger.log(
                    "Delivering deferred deep link: ${deepLinkData?.shortCode ?: "organic"}"
                )

                deferredDeepLinkCallbacks.toList()
            }

            withContext(Dispatchers.Main) {
                callbacks.forEach { it(deepLinkData) }
            }
        }
    }

    // -- Direct Deep Link (App Links, Custom Schemes) --

    /**
     * Registers a callback for direct deep links.
     */
    fun onDeepLink(callback: DeepLinkCallback) {
        synchronized(this) {
            deepLinkCallbacks.add(callback)
        }
    }

    /**
     * Handles a deep link URI with server-side resolution.
     */
    fun handleDeepLink(uri: Uri) {
        scope.launch {
            LinkFortyLogger.log("Handling deep link: $uri")

            // Parse locally first as fallback
            val localData = UrlParser.parseDeepLink(uri)

            // Attempt server-side resolution if configured
            val resolvedData = if (networkManager != null && fingerprintCollector != null) {
                resolveUrl(uri, fallback = localData)
            } else {
                localData
            }

            // Snapshot callbacks under lock, then deliver outside
            val callbacks = synchronized(this@DeepLinkHandler) {
                deepLinkCallbacks.toList()
            }

            if (resolvedData != null) {
                LinkFortyLogger.log("Parsed deep link: $resolvedData")
            } else {
                LinkFortyLogger.log("Failed to parse deep link URL")
            }

            withContext(Dispatchers.Main) {
                callbacks.forEach { it(uri, resolvedData) }
            }
        }
    }

    /**
     * Clears all registered callbacks (for testing / reset).
     */
    fun clearCallbacks() {
        synchronized(this) {
            deferredDeepLinkCallbacks.clear()
            deepLinkCallbacks.clear()
            deferredDeepLinkDelivered = false
            cachedDeferredDeepLink = null
        }
    }

    // -- Private --

    private suspend fun resolveUrl(uri: Uri, fallback: DeepLinkData?): DeepLinkData? {
        val networkManager = networkManager ?: return fallback
        val fingerprintCollector = fingerprintCollector ?: return fallback

        // Extract path segments
        val pathSegments = uri.pathSegments ?: return fallback
        if (pathSegments.isEmpty()) return fallback

        // Build resolve path: /api/sdk/v1/resolve/{templateSlug?}/{shortCode}
        val resolvePath = if (pathSegments.size >= 2) {
            val templateSlug = pathSegments[pathSegments.size - 2]
            val shortCode = pathSegments[pathSegments.size - 1]
            "/api/sdk/v1/resolve/$templateSlug/$shortCode"
        } else {
            val shortCode = pathSegments[0]
            "/api/sdk/v1/resolve/$shortCode"
        }

        // Collect fingerprint for query parameters
        val fingerprint = fingerprintCollector.collectFingerprint(
            attributionWindowHours = 168,
            deviceId = null
        )

        // Build query string
        val queryString = listOf(
            "fp_tz" to fingerprint.timezone,
            "fp_lang" to fingerprint.language,
            "fp_sw" to fingerprint.screenWidth.toString(),
            "fp_sh" to fingerprint.screenHeight.toString(),
            "fp_platform" to fingerprint.platform,
            "fp_pv" to fingerprint.platformVersion
        ).joinToString("&") { (k, v) ->
            "$k=${URLEncoder.encode(v, Charsets.UTF_8.name())}"
        }

        val endpoint = "$resolvePath?$queryString"

        return try {
            val resolved: DeepLinkData = networkManager.request(
                endpoint = endpoint,
                method = HttpMethod.GET
            )
            LinkFortyLogger.log("Server-side resolution succeeded for $uri")
            resolved
        } catch (e: Exception) {
            LinkFortyLogger.log("Server-side resolution failed, using local parse: ${e.message}")
            fallback
        }
    }
}
