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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

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

    /** One flush at a time, so concurrent triggers send each queued event once. */
    private val flushMutex = Mutex()

    /**
     * Events a [trackEvent] call is waiting on, by clientEventId, with the
     * server's refusal once a flush records one.
     */
    private val awaiting = ConcurrentHashMap<String, Optional<LinkFortyError>>()

    /**
     * Tracks a custom event.
     *
     * If it can't be sent now (no connection, or a 5xx response), it is queued
     * on the device and the call returns normally; the queue is sent later.
     *
     * @param name Event name (e.g., "purchase", "signup")
     * @param properties Optional event properties (must be JSON-serializable)
     * @throws LinkFortyError for an empty name, when not initialized, or when the
     *   server rejects the event (a 4xx), which is not queued
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

        // Stored before it is sent, so an app closed mid-send keeps it; the
        // server stores a resent event once, by its clientEventId.
        if (!eventQueue.enqueue(event)) {
            sendNow(event)
            return
        }
        awaiting[event.clientEventId] = Optional.empty()
        try {
            flushQueue()
        } finally {
            awaiting.remove(event.clientEventId)?.orElse(null)?.let { throw it }
        }
        LinkFortyLogger.log("Event tracked: $name")
    }

    /** Sends an event the full queue had no room for: reported, not retried. */
    private suspend fun sendNow(event: EventRequest) {
        try {
            sendEvent(event)
        } catch (e: LinkFortyError) {
            if (!isRetryable(e)) throw e
            LinkFortyLogger.log("Event queue full and the send failed; dropping ${event.eventName}: $e")
        }
    }

    /**
     * Tracks a revenue event. Queued like [trackEvent] when it can't be sent now.
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
     * @throws LinkFortyError as [trackEvent] does
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
     * Flushes the event queue, attempting to send all queued events. Flushes run
     * one at a time.
     */
    suspend fun flushQueue() = flushMutex.withLock {
        if (eventQueue.isEmpty) return@withLock
        LinkFortyLogger.log("Flushing event queue (${eventQueue.count} events)")

        while (true) {
            val event = eventQueue.first() ?: break
            try {
                sendEvent(event)
                eventQueue.remove(event.clientEventId)
                LinkFortyLogger.log("Queued event sent: ${event.eventName}")
            } catch (e: LinkFortyError) {
                if (isRetryable(e)) {
                    // Keep it at the head and stop: the rest would fail the same way.
                    LinkFortyLogger.log("Failed to send queued event, keeping it queued: $e")
                    return@withLock
                }
                eventQueue.remove(event.clientEventId)
                awaiting.computeIfPresent(event.clientEventId) { _, _ -> Optional.of(e) }
                LinkFortyLogger.log("Dropping queued event the server refused: $e")
            }
        }
    }

    /** Returns the number of queued events. */
    val queuedEventCount: Int get() = eventQueue.count

    /** Clears the event queue. */
    fun clearQueue() {
        eventQueue.clear()
    }

    /** Network failures and 5xx responses; a 4xx would be refused again. */
    private fun isRetryable(error: LinkFortyError): Boolean = when (error) {
        is LinkFortyError.NetworkError -> true
        is LinkFortyError.InvalidResponse -> error.statusCode == null || error.statusCode >= 500
        else -> false
    }

    private suspend fun sendEvent(event: EventRequest) {
        networkManager.request<EventResponse>(
            endpoint = "/api/sdk/v1/event",
            method = HttpMethod.POST,
            body = event
        )
    }
}
