package com.linkforty.sdk.events

import com.linkforty.sdk.LinkFortyLogger
import com.linkforty.sdk.models.EventRequest

/**
 * Queue for storing events when offline.
 * Thread-safe via synchronized blocks.
 */
internal class EventQueue {

    /** Maximum number of events to queue */
    private val maxQueueSize = 100

    /** Queued events */
    private val queue = ArrayDeque<EventRequest>()

    /**
     * Adds an event to the queue.
     * @return true if added, false if queue is full
     */
    @Synchronized
    fun enqueue(event: EventRequest): Boolean {
        if (queue.size >= maxQueueSize) {
            LinkFortyLogger.log("Event queue full, dropping event: ${event.eventName}")
            return false
        }
        queue.addLast(event)
        LinkFortyLogger.log("Event queued: ${event.eventName} (queue size: ${queue.size})")
        return true
    }

    /**
     * Dequeues the oldest event.
     * @return the oldest event, or null if queue is empty
     */
    @Synchronized
    fun dequeue(): EventRequest? {
        return if (queue.isEmpty()) null else queue.removeFirst()
    }

    /**
     * Returns all queued events without removing them.
     */
    @Synchronized
    fun peek(): List<EventRequest> = queue.toList()

    /**
     * Clears all events from the queue.
     */
    @Synchronized
    fun clear() {
        val size = queue.size
        queue.clear()
        if (size > 0) {
            LinkFortyLogger.log("Event queue cleared ($size events removed)")
        }
    }

    /** Returns the number of queued events. */
    val count: Int
        @Synchronized get() = queue.size

    /** Checks if the queue is empty. */
    val isEmpty: Boolean
        @Synchronized get() = queue.isEmpty()

    /** Checks if the queue is full. */
    val isFull: Boolean
        @Synchronized get() = queue.size >= maxQueueSize
}
