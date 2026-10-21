package com.linkforty.sdk.events

import com.linkforty.sdk.LinkFortyLogger
import com.linkforty.sdk.models.AnyJsonAdapter
import com.linkforty.sdk.models.EventRequest
import com.linkforty.sdk.storage.StorageManagerProtocol
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

/**
 * Keeps the event queue on the device, so events not yet sent survive the app
 * being closed.
 */
internal class EventQueueStore(private val storage: StorageManagerProtocol) {

    private val adapter: JsonAdapter<List<EventRequest>> = Moshi.Builder()
        .add(AnyJsonAdapter())
        .build()
        .adapter(Types.newParameterizedType(List::class.java, EventRequest::class.java))

    fun save(events: List<EventRequest>) {
        if (events.isEmpty()) {
            storage.removeEventQueue()
            return
        }
        try {
            storage.saveEventQueue(adapter.toJson(events))
        } catch (e: Exception) {
            LinkFortyLogger.log("Could not store the event queue: $e")
        }
    }

    fun load(): List<EventRequest> {
        val json = storage.getEventQueue() ?: return emptyList()
        return try {
            adapter.fromJson(json).orEmpty().map { it.copy(eventData = restoreNumbers(it.eventData)) }
        } catch (e: Exception) {
            LinkFortyLogger.log("Discarding an unreadable stored event queue: $e")
            storage.removeEventQueue()
            emptyList()
        }
    }

    fun clear() {
        storage.removeEventQueue()
    }

    /**
     * JSON has one number type, read back as a Double; a whole number goes back
     * to being sent as an integer, as it was when the event was tracked.
     */
    @Suppress("UNCHECKED_CAST")
    private fun restoreNumbers(value: Map<String, Any>): Map<String, Any> =
        value.mapValues { (_, v) -> restoreNumber(v) } as Map<String, Any>

    private fun restoreNumber(value: Any?): Any? = when (value) {
        is Double -> if (value % 1.0 == 0.0 && value >= Long.MIN_VALUE && value <= Long.MAX_VALUE) value.toLong() else value
        is List<*> -> value.map { restoreNumber(it) }
        is Map<*, *> -> value.entries.associate { (k, v) -> k.toString() to restoreNumber(v) }
        else -> value
    }
}
