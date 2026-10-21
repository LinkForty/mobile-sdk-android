package com.linkforty.sdk

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Bundle
import com.linkforty.sdk.attribution.AttributionContext
import com.linkforty.sdk.attribution.AttributionManager
import com.linkforty.sdk.deeplink.DeepLinkCallback
import com.linkforty.sdk.deeplink.DeepLinkHandler
import com.linkforty.sdk.deeplink.DeferredDeepLinkCallback
import com.linkforty.sdk.errors.LinkFortyError
import com.linkforty.sdk.events.EventQueue
import com.linkforty.sdk.events.EventQueueStore
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
        /**
         * The shared SDK instance. Available before [initialize]: callbacks
         * registered and links handled before then are kept until it runs.
         */
        @JvmStatic
        val shared: LinkForty = LinkForty()

        /** Serializes [initialize] calls, so two at once can't both report the install. */
        private val initMutex = Mutex()

        /**
         * Initializes the SDK with configuration and reports the install.
         *
         * If reporting the install fails (for example, offline on the first
         * launch), the SDK is left uninitialized, keeping registered callbacks,
         * and the call can be retried.
         *
         * @param context Android context (stored as applicationContext)
         * @param config SDK configuration
         * @param attributionWindowHours Attribution window in hours; defaults to
         *   [LinkFortyConfig.attributionWindowHours]
         * @param deviceId Optional device identifier for attribution
         * @return [InstallResponse] with attribution data
         * @throws LinkFortyError if initialization fails
         */
        suspend fun initialize(
            context: Context,
            config: LinkFortyConfig,
            attributionWindowHours: Int? = null,
            deviceId: String? = null
        ): InstallResponse = initMutex.withLock {
            if (shared.isInitialized) throw LinkFortyError.AlreadyInitialized()
            shared.doInitialize(context, config, attributionWindowHours ?: config.attributionWindowHours, deviceId)
        }

        /**
         * Returns whether this is the app's first launch since installation.
         *
         * Usable before [initialize]. Once the SDK is initialized it returns the
         * value read at the start of this launch, so it stays true for the whole
         * first launch.
         *
         * @param context Android context
         */
        fun isFirstLaunch(context: Context): Boolean {
            if (shared.isInitialized) return shared.isFirstLaunch()
            return StorageManager(context.applicationContext).isFirstLaunch()
        }
    }

    // -- Internal state --

    private var config: LinkFortyConfig? = null
    private var networkManager: NetworkManager? = null
    private var attributionManager: AttributionManager? = null
    private var attributionContext: AttributionContext? = null
    private var eventTracker: EventTracker? = null
    private var externalUserId: String? = null

    /** Guards [isInitialized], [deepLinkHandler] and [pendingDeepLinks]. */
    private val lock = Any()

    @Volatile
    private var isInitialized = false

    /**
     * Created up front so callbacks registered before [initialize] are kept;
     * [initialize] configures it.
     */
    @Volatile
    private var deepLinkHandler = DeepLinkHandler()

    /**
     * Links handed to [handleDeepLink] before [initialize], as when a link
     * launches the app. Resolved once the SDK is configured.
     */
    private val pendingDeepLinks = mutableListOf<Uri>()

    /** Whether this launch is the app's first, read before the install is reported. */
    private var firstLaunchThisSession = true

    /** Runs queued-event flushes the app didn't ask for (after initialize, on foreground). */
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Whether the foreground observer is registered; once per process. */
    private var foregroundObserverRegistered = false

    private suspend fun doInitialize(
        context: Context,
        config: LinkFortyConfig,
        attributionWindowHours: Int,
        deviceId: String?
    ): InstallResponse {
        // Validate configuration
        config.validate()

        val appContext = context.applicationContext

        // Store configuration
        this.config = config

        // Set debug mode
        LinkFortyLogger.isDebugEnabled = config.debug

        // Create managers
        val storageManager = StorageManager(appContext)
        firstLaunchThisSession = storageManager.isFirstLaunch()
        val networkManager = NetworkManager(config)
        val fingerprintCollector = FingerprintCollector(appContext)

        this.networkManager = networkManager

        val attributionContext = AttributionContext(storageManager, config.debug)
        this.attributionContext = attributionContext

        this.attributionManager = AttributionManager(
            networkManager = networkManager,
            storageManager = storageManager,
            fingerprintCollector = fingerprintCollector
        )

        this.eventTracker = EventTracker(
            networkManager = networkManager,
            storageManager = storageManager,
            attributionContext = attributionContext,
            eventQueue = EventQueue(EventQueueStore(storageManager))
        )

        val handler = deepLinkHandler
        handler.configure(
            networkManager = networkManager,
            fingerprintCollector = fingerprintCollector,
            baseURL = config.baseURL,
            attributionContext = attributionContext
        )

        // Mark as initialized and resolve links that arrived before now
        val pending = synchronized(lock) {
            isInitialized = true
            pendingDeepLinks.toList().also { pendingDeepLinks.clear() }
        }
        pending.forEach { handler.handleDeepLink(it) }

        // Only the launch that reports the install delivers a deferred deep link;
        // later launches get the cached attribution without a callback.
        val isNewInstall = attributionManager!!.getInstallId() == null

        // Report install and get attribution data
        val response = try {
            attributionManager!!.reportInstall(
                attributionWindowHours = attributionWindowHours,
                deviceId = deviceId,
                appToken = config.appToken
            )
        } catch (e: Throwable) {
            // Leave the SDK uninitialized, keeping callbacks, so the app can call initialize() again.
            tearDownManagers()
            throw e
        }

        // The deferred deep link, or null for an organic install
        if (isNewInstall) {
            handler.deliverDeferredDeepLink(response.deepLinkData.takeIf { response.attributed })
        }

        LinkFortyLogger.log("SDK initialized successfully (attributed: ${response.attributed})")

        // Events queued before this launch, or before the install was reported, go out now.
        flushQueuedEventsInBackground()
        observeForeground(appContext)

        return response
    }

    private fun flushQueuedEventsInBackground() {
        val tracker = eventTracker ?: return
        if (tracker.queuedEventCount == 0) return
        backgroundScope.launch {
            runCatching { tracker.flushQueue() }
                .onFailure { LinkFortyLogger.log("Background event flush failed: $it") }
        }
    }

    /**
     * Sends queued events each time the app returns to the foreground, counted
     * from activities started and stopped. Needs no permission.
     */
    private fun observeForeground(context: Context) {
        val application = context as? Application ?: return
        synchronized(lock) {
            if (foregroundObserverRegistered) return
            foregroundObserverRegistered = true
        }
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var started = 0

            override fun onActivityStarted(activity: Activity) {
                started++
                if (started == 1 && isInitialized) flushQueuedEventsInBackground()
            }

            override fun onActivityStopped(activity: Activity) {
                if (started > 0) started--
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    // -- Deep Linking --

    /**
     * Handles a deep link URI (App Link or custom scheme).
     *
     * A link handed over before [initialize], as when it launches the app, is
     * held and resolved once [initialize] has configured the SDK.
     *
     * @param uri Deep link URI to handle
     */
    fun handleDeepLink(uri: Uri) {
        val handler = synchronized(lock) {
            if (!isInitialized) {
                pendingDeepLinks.add(uri)
                null
            } else {
                deepLinkHandler
            }
        }
        if (handler == null) {
            LinkFortyLogger.log("Deep link held until initialize(): $uri")
            return
        }
        handler.handleDeepLink(uri)
    }

    /**
     * Registers a callback for deferred deep links, called once on the launch
     * that reports the install: with the link's data, or null for an organic
     * install. Can be registered before or after [initialize].
     *
     * @param callback Callback invoked with deep link data, or null for organic installs
     */
    fun onDeferredDeepLink(callback: DeferredDeepLinkCallback) {
        deepLinkHandler.onDeferredDeepLink(callback)
    }

    /**
     * Registers a callback for direct deep links (triggered when app opens from
     * link). Can be registered before or after [initialize].
     *
     * @param callback Callback invoked with URI and deep link data
     */
    fun onDeepLink(callback: DeepLinkCallback) {
        deepLinkHandler.onDeepLink(callback)
    }

    // -- User Identity --

    /**
     * Sets the external user ID for attribution. This ID will be attached to all
     * links created via [createLink] unless overridden per-call. Pass null to clear.
     *
     * @param id External user ID string, or null to clear
     */
    fun setExternalUserId(id: String?) {
        this.externalUserId = id
        LinkFortyLogger.log("External user ID set: $id")
    }

    /**
     * Returns the current external user ID, if set.
     */
    fun getExternalUserId(): String? = externalUserId

    // -- Event Tracking --

    /**
     * Tracks a custom event.
     *
     * The event is stored on the device and sent. If it can't be sent now (no
     * connection, or a 5xx response), it stays queued and the call returns
     * normally; the queue survives the app being closed and is sent on the next
     * launch, when the app returns to the foreground, or after the next event
     * that sends. A retried event is stored once by the server.
     *
     * @param name Event name (e.g., "purchase", "signup")
     * @param properties Optional event properties (must be JSON-serializable)
     * @throws LinkFortyError for an empty name, when not initialized, or when the
     *   server rejects the event (a 4xx); a rejected event is not queued
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
     * @throws LinkFortyError as [trackEvent] does, or for a negative amount
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
     * Tracks a screen view for per-link screen-flow funnels.
     *
     * Emits a `screen_view` event stamped with the active last-click attribution
     * context, so the dashboard can show which screens users reach after opening a
     * deep link. Call from your screen's lifecycle (e.g., `onResume`) or a
     * `NavController.OnDestinationChangedListener`.
     *
     * @param name Screen name (e.g., "ProductDetail")
     * @param properties Optional additional properties
     * @throws LinkFortyError as [trackEvent] does
     */
    suspend fun trackScreenView(name: String, properties: Map<String, Any>? = null) {
        if (!isInitialized) throw LinkFortyError.NotInitialized()
        eventTracker?.trackScreenView(name, properties)
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
     * Requires an API key in [LinkFortyConfig]. Posts to the SDK link endpoint
     * (`POST /api/sdk/v1/links`), which uses [CreateLinkOptions.templateId] when
     * given and the organization's most recent template otherwise. A server
     * without that endpoint (404) gets `POST /api/links` instead.
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

        // Per-call externalUserId takes precedence, then fall back to SDK-level value
        val resolvedOptions = if (options.externalUserId == null && externalUserId != null) {
            options.copy(externalUserId = externalUserId)
        } else {
            options
        }

        return try {
            networkManager.request(
                endpoint = "/api/sdk/v1/links",
                method = HttpMethod.POST,
                body = resolvedOptions
            )
        } catch (e: LinkFortyError.InvalidResponse) {
            if (e.statusCode != 404) throw e
            LinkFortyLogger.log("Server has no SDK link endpoint; creating the link with POST /api/links")
            createDashboardLink(networkManager, config, resolvedOptions)
        }
    }

    /** `POST /api/links`, for servers without the SDK link endpoint. */
    private suspend fun createDashboardLink(
        networkManager: NetworkManager,
        config: LinkFortyConfig,
        options: CreateLinkOptions
    ): CreateLinkResult {
        val response: DashboardCreateLinkResponse = networkManager.request(
            endpoint = "/api/links",
            method = HttpMethod.POST,
            body = options
        )

        // The server's short URL carries the link's own domain; servers that
        // don't return one get it built from the base URL.
        val url = response.shortUrl ?: run {
            val baseUrl = config.baseURL.trimEnd('/')
            val templateSlug = options.templateSlug ?: ""
            val pathSegment = if (templateSlug.isEmpty()) {
                response.shortCode
            } else {
                "$templateSlug/${response.shortCode}"
            }
            "$baseUrl/$pathSegment"
        }

        return CreateLinkResult(
            url = url,
            shortCode = response.shortCode,
            linkId = response.id,
            deduplicated = null
        )
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
     * Returns whether this is the app's first launch since installation.
     *
     * Read once at the start of [initialize], before the install is reported, so
     * it stays true for the whole first launch. To check before initializing, use
     * [LinkForty.isFirstLaunch] with a context.
     */
    fun isFirstLaunch(): Boolean = firstLaunchThisSession

    // -- Data Management --

    /**
     * Clears all stored SDK data: the install ID, install attribution, queued
     * events and the last-click attribution context. The next launch reports a
     * new install. Registered callbacks stay registered.
     */
    fun clearData() {
        attributionManager?.clearData()
        attributionContext?.clear()
        eventTracker?.clearQueue()
        deepLinkHandler.clearDeferredDeepLink()
        externalUserId = null
        LinkFortyLogger.log("All SDK data cleared")
    }

    /**
     * Resets the SDK to uninitialized state, removing registered callbacks.
     * Note: This does NOT clear stored data. Call [clearData] first if needed.
     */
    fun reset() {
        tearDownManagers()
        synchronized(lock) {
            deepLinkHandler = DeepLinkHandler()
            pendingDeepLinks.clear()
        }
        externalUserId = null
        firstLaunchThisSession = true
        LinkFortyLogger.log("SDK reset to uninitialized state")
    }

    /**
     * Returns the SDK to uninitialized state, keeping registered callbacks, so
     * [initialize] can be called again.
     */
    private fun tearDownManagers() {
        synchronized(lock) { isInitialized = false }
        config = null
        networkManager = null
        attributionManager = null
        attributionContext = null
        eventTracker = null
    }
}
