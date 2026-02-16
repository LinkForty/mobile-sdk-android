package com.linkforty.sdk.events

import com.linkforty.sdk.LinkFortyLogger
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
    private val eventQueue: EventQueue = EventQueue()
) {

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

        // Create event request
        val event = EventRequest(
            installId = installId,
            eventName = name,
            eventData = properties ?: emptyMap(),
            timestamp = Instant.now().toString()
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
