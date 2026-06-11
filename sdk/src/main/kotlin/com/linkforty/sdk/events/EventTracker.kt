package com.linkforty.sdk.events

import com.linkforty.sdk.LinkFortyLogger
import com.linkforty.sdk.attribution.AttributionContext
import com.linkforty.sdk.errors.LinkFortyError
import com.linkforty.sdk.models.EventRequest
import com.linkforty.sdk.models.EventResponse
import com.linkforty.sdk.network.HttpMethod
import com.linkforty.sdk.network.NetworkManagerProtocol
import com.linkforty.sdk.network.request
import com.linkforty.sdk.storage.StorageManagerProtocol
import java.math.BigDecimal
import java.time.Instant

/**
 * Tracks custom events and manages event queueing.
 */
internal class EventTracker(
    private val networkManager: NetworkManagerProtocol,
    private val storageManager: StorageManagerProtocol,
    private val attributionContext: AttributionContext,
    private val eventQueue: EventQueue = EventQueue()
) {

    /** Last tracked screen name, for the `previousScreen` transition stamp. */
    private var lastScreen: String? = null

    /**
     * Tracks a custom event.
     *
     * @param name Event name (e.g., "purchase", "signup")
     * @param properties Optional event properties (must be JSON-serializable)
     * @throws LinkFortyError if tracking fails
     */
    suspend fun trackEvent(name: String, properties: Map<String, Any>? = null) {
        // Validate event name
        if (name.isBlank()) {
            throw LinkFortyError.InvalidEventData("Event name cannot be empty")
        }

        // Get install ID
        val installId = storageManager.getInstallId()
            ?: throw LinkFortyError.NotInitialized()

        // Stamp the event with the active last-click attribution context so the
        // backend can credit the deep link that drove it (organic events carry
        // only the session id).
        val stamp = attributionContext.getStamp()
        val event = EventRequest(
            installId = installId,
            eventName = name,
            eventData = properties ?: emptyMap(),
            timestamp = Instant.now().toString(),
            attributedLinkId = stamp.attributedLinkId,
            attributedClickId = stamp.attributedClickId,
            linkOpenedAt = stamp.linkOpenedAt,
            sessionId = stamp.sessionId
        )

        // Try to send immediately
        try {
            sendEvent(event)
            LinkFortyLogger.log("Event tracked: $name")

            // If send succeeds, try to flush queue
            flushQueue()
        } catch (e: Exception) {
            // If send fails, queue the event
            eventQueue.enqueue(event)
            LinkFortyLogger.log("Event queued due to error: $e")
            throw e
        }
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
        if (amount < BigDecimal.ZERO) {
            throw LinkFortyError.InvalidEventData("Revenue amount must be non-negative")
        }

        val eventProperties = (properties ?: emptyMap()).toMutableMap()
        eventProperties["revenue"] = amount.toDouble()
        eventProperties["currency"] = currency

        trackEvent(name = "revenue", properties = eventProperties)
    }

    /**
     * Tracks a screen view.
     *
     * Emits a `screen_view` event (through the normal pipeline, so it is stamped
     * with the active last-click attribution context) carrying the screen name and
     * — when available — the previously tracked screen, so the dashboard can build
     * a per-link screen-flow funnel.
     *
     * @param name Screen name (e.g., "ProductDetail")
     * @param properties Optional additional properties
     * @throws LinkFortyError if tracking fails
     */
    suspend fun trackScreenView(name: String, properties: Map<String, Any>? = null) {
        if (name.isBlank()) {
            throw LinkFortyError.InvalidEventData("Screen name cannot be empty")
        }

        val previous = synchronized(this) {
            val p = lastScreen
            lastScreen = name
            p
        }

        val eventProperties = (properties ?: emptyMap()).toMutableMap()
        eventProperties["screen"] = name
        if (previous != null && previous != name) {
            eventProperties["previousScreen"] = previous
        }

        trackEvent(name = "screen_view", properties = eventProperties)
    }

    /**
     * Flushes the event queue, attempting to send all queued events.
     */
    suspend fun flushQueue() {
        LinkFortyLogger.log("Flushing event queue (${eventQueue.count} events)")

        while (!eventQueue.isEmpty) {
            val event = eventQueue.dequeue() ?: break
            try {
                sendEvent(event)
                LinkFortyLogger.log("Queued event sent: ${event.eventName}")
            } catch (e: Exception) {
                // Re-queue if send fails
                eventQueue.enqueue(event)
                LinkFortyLogger.log("Failed to send queued event: $e")
                return
            }
        }
    }

    /** Returns the number of queued events. */
    val queuedEventCount: Int get() = eventQueue.count

    /** Clears the event queue. */
    fun clearQueue() {
        eventQueue.clear()
    }

    private suspend fun sendEvent(event: EventRequest) {
        networkManager.request<EventResponse>(
            endpoint = "/api/sdk/v1/event",
            method = HttpMethod.POST,
            body = event
        )
    }
}
