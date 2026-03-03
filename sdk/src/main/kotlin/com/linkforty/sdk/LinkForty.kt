package com.linkforty.sdk

import android.content.Context
import android.net.Uri
import com.linkforty.sdk.attribution.AttributionManager
import com.linkforty.sdk.deeplink.DeepLinkCallback
import com.linkforty.sdk.deeplink.DeepLinkHandler
import com.linkforty.sdk.deeplink.DeferredDeepLinkCallback
import com.linkforty.sdk.errors.LinkFortyError
import com.linkforty.sdk.events.EventTracker
import com.linkforty.sdk.fingerprint.FingerprintCollector
import com.linkforty.sdk.models.CreateLinkOptions
import com.linkforty.sdk.models.CreateLinkResult
import com.linkforty.sdk.models.DashboardCreateLinkResponse
import com.linkforty.sdk.models.DeepLinkData
import com.linkforty.sdk.models.InstallResponse
import com.linkforty.sdk.models.LinkFortyConfig
import com.linkforty.sdk.network.HttpMethod
import com.linkforty.sdk.network.NetworkManager
import com.linkforty.sdk.storage.StorageManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal

/**
 * Main SDK class — singleton interface for LinkForty.
 *
 * Usage:
 * ```kotlin
 * val config = LinkFortyConfig(
 *     baseURL = "https://go.yourdomain.com",
 *     apiKey = "your-api-key"
 * )
 * val response = LinkForty.initialize(context, config)
 * ```
 */
class LinkForty private constructor() {

    companion object {
        @Volatile
        private var instance: LinkForty? = null

        /**
         * The shared SDK instance. Throws [LinkFortyError.NotInitialized] if not yet initialized.
         */
        val shared: LinkForty
            get() = instance ?: throw LinkFortyError.NotInitialized()

        /**
         * Initializes the SDK with configuration and reports the install.
         *
         * @param context Android context (stored as applicationContext)
         * @param config SDK configuration
         * @param attributionWindowHours Attribution window in hours (default: 168 = 7 days)
         * @param deviceId Optional device identifier for attribution
         * @return [InstallResponse] with attribution data
         * @throws LinkFortyError if initialization fails
         */
        suspend fun initialize(
            context: Context,
            config: LinkFortyConfig,
            attributionWindowHours: Int = 168,
            deviceId: String? = null
        ): InstallResponse {
            val sdk = LinkForty()
            return sdk.doInitialize(context, config, attributionWindowHours, deviceId)
        }
    }

    // -- Internal state --

    private var config: LinkFortyConfig? = null
    private var networkManager: NetworkManager? = null
    private var attributionManager: AttributionManager? = null
    private var eventTracker: EventTracker? = null
    private var deepLinkHandler: DeepLinkHandler? = null
    private val mutex = Mutex()
    private var isInitialized = false

    private suspend fun doInitialize(
        context: Context,
        config: LinkFortyConfig,
        attributionWindowHours: Int,
        deviceId: String?
    ): InstallResponse {
        mutex.withLock {
            // Check if already initialized
            if (instance != null) {
                throw LinkFortyError.AlreadyInitialized()
            }

            // Validate configuration
            config.validate()

            val appContext = context.applicationContext

            // Store configuration
            this.config = config

            // Set debug mode
            LinkFortyLogger.isDebugEnabled = config.debug

            // Create managers
            val storageManager = StorageManager(appContext)
            val networkManager = NetworkManager(config)
            val fingerprintCollector = FingerprintCollector(appContext)

            this.networkManager = networkManager

            this.attributionManager = AttributionManager(
                networkManager = networkManager,
                storageManager = storageManager,
                fingerprintCollector = fingerprintCollector
            )

            this.eventTracker = EventTracker(
                networkManager = networkManager,
                storageManager = storageManager
            )

            val handler = DeepLinkHandler()
            handler.configure(
                networkManager = networkManager,
                fingerprintCollector = fingerprintCollector,
                baseURL = config.baseURL
            )
            this.deepLinkHandler = handler

            // Mark as initialized
            this.isInitialized = true
            instance = this
        }

        // Report install and get attribution data (outside the lock)
        val response = attributionManager!!.reportInstall(
            attributionWindowHours = attributionWindowHours,
            deviceId = deviceId
        )

        // If attributed, notify deferred deep link handler
        if (response.attributed && response.deepLinkData != null) {
            deepLinkHandler?.deliverDeferredDeepLink(response.deepLinkData)
        }

        LinkFortyLogger.log("SDK initialized successfully (attributed: ${response.attributed})")

        return response
    }

    // -- Deep Linking --

    /**
     * Handles a deep link URI (App Link or custom scheme).
     *
     * @param uri Deep link URI to handle
     */
    fun handleDeepLink(uri: Uri) {
        if (!isInitialized) {
            LinkFortyLogger.log("SDK not initialized. Call initialize() first.")
            return
        }
        deepLinkHandler?.handleDeepLink(uri)
    }

    /**
     * Registers a callback for deferred deep links (triggered on first launch after attributed install).
     *
     * @param callback Callback invoked with deep link data, or null for organic installs
     */
    fun onDeferredDeepLink(callback: DeferredDeepLinkCallback) {
        if (!isInitialized) {
            LinkFortyLogger.log("SDK not initialized. Call initialize() first.")
            return
        }
        deepLinkHandler?.onDeferredDeepLink(callback)
    }

    /**
     * Registers a callback for direct deep links (triggered when app opens from link).
     *
     * @param callback Callback invoked with URI and deep link data
     */
    fun onDeepLink(callback: DeepLinkCallback) {
        if (!isInitialized) {
            LinkFortyLogger.log("SDK not initialized. Call initialize() first.")
            return
        }
        deepLinkHandler?.onDeepLink(callback)
    }

    // -- Event Tracking --

    /**
     * Tracks a custom event.
     *
     * @param name Event name (e.g., "purchase", "signup")
     * @param properties Optional event properties (must be JSON-serializable)
     * @throws LinkFortyError if tracking fails
     */
    suspend fun trackEvent(name: String, properties: Map<String, Any>? = null) {
        if (!isInitialized) throw LinkFortyError.NotInitialized()
        eventTracker?.trackEvent(name, properties)
    }

    /**
     * Tracks a revenue event.
     *
     * @param amount Revenue amount (must be non-negative)
     * @param currency Currency code (e.g., "USD")
     * @param properties Optional additional properties
     * @throws LinkFortyError if tracking fails
     */
    suspend fun trackRevenue(
        amount: BigDecimal,
        currency: String,
        properties: Map<String, Any>? = null
    ) {
        if (!isInitialized) throw LinkFortyError.NotInitialized()
        eventTracker?.trackRevenue(amount, currency, properties)
    }

    /**
     * Flushes the event queue, attempting to send all queued events.
     */
    suspend fun flushEvents() {
        if (!isInitialized) {
            LinkFortyLogger.log("SDK not initialized. Call initialize() first.")
            return
        }
        eventTracker?.flushQueue()
    }

    /**
     * Returns the number of queued events.
     */
    val queuedEventCount: Int
        get() {
            if (!isInitialized) return 0
            return eventTracker?.queuedEventCount ?: 0
        }

    /**
     * Clears the event queue without sending events.
     */
    fun clearEventQueue() {
        if (!isInitialized) {
            LinkFortyLogger.log("SDK not initialized. Call initialize() first.")
            return
        }
        eventTracker?.clearQueue()
    }

    // -- Link Creation --

    /**
     * Creates a short link programmatically.
     *
     * Requires an API key in [LinkFortyConfig].
     * If [CreateLinkOptions.templateId] is provided, uses the dashboard endpoint (`POST /api/links`).
     * Otherwise, uses the simplified SDK endpoint (`POST /api/sdk/v1/links`)
     * which auto-selects the organization's most recent template.
     *
     * @param options Link creation options
     * @return [CreateLinkResult] with the shareable URL, short code, and link ID
     * @throws LinkFortyError.NotInitialized if SDK not initialized
     * @throws LinkFortyError.MissingApiKey if no API key configured
     */
    suspend fun createLink(options: CreateLinkOptions): CreateLinkResult {
        if (!isInitialized) throw LinkFortyError.NotInitialized()

        val config = config ?: throw LinkFortyError.NotInitialized()
        if (config.apiKey == null) throw LinkFortyError.MissingApiKey()

        val networkManager = networkManager ?: throw LinkFortyError.NotInitialized()

        return if (options.templateId != null) {
            // Use dashboard endpoint with explicit templateId
            val response: DashboardCreateLinkResponse = networkManager.request(
                endpoint = "/api/links",
                method = HttpMethod.POST,
                body = options
            )

            // Construct URL from parts
            val baseUrl = config.baseURL.trimEnd('/')
            val templateSlug = options.templateSlug ?: ""
            val pathSegment = if (templateSlug.isEmpty()) {
                response.shortCode
            } else {
                "$templateSlug/${response.shortCode}"
            }
            val url = "$baseUrl/$pathSegment"

            CreateLinkResult(
                url = url,
                shortCode = response.shortCode,
                linkId = response.id,
                deduplicated = null
            )
        } else {
            // Use simplified SDK endpoint (auto-selects template)
            networkManager.request(
                endpoint = "/api/sdk/v1/links",
                method = HttpMethod.POST,
                body = options
            )
        }
    }

    // -- Attribution Data --

    /**
     * Returns the install ID if available.
     */
    fun getInstallId(): String? {
        if (!isInitialized) return null
        return attributionManager?.getInstallId()
    }

    /**
     * Returns the install attribution data if available.
     */
    fun getInstallData(): DeepLinkData? {
        if (!isInitialized) return null
        return attributionManager?.getInstallData()
    }

    /**
     * Returns whether this is the first launch.
     */
    fun isFirstLaunch(): Boolean {
        if (!isInitialized) return true
        return attributionManager?.isFirstLaunch() ?: true
    }

    // -- Data Management --

    /**
     * Clears all stored SDK data.
     */
    fun clearData() {
        attributionManager?.clearData()
        eventTracker?.clearQueue()
        deepLinkHandler?.clearCallbacks()
        LinkFortyLogger.log("All SDK data cleared")
    }

    /**
     * Resets the SDK to uninitialized state.
     * Note: This does NOT clear stored data. Call [clearData] first if needed.
     */
    fun reset() {
        config = null
        networkManager = null
        attributionManager = null
        eventTracker = null
        deepLinkHandler = null
        isInitialized = false
        instance = null
        LinkFortyLogger.log("SDK reset to uninitialized state")
    }
}
