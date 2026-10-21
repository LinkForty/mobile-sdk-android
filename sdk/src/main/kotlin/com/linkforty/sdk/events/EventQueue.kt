package com.linkforty.sdk.events

import com.linkforty.sdk.LinkFortyLogger
import com.linkforty.sdk.models.EventRequest

/**
 * Queue for storing events when offline.
 * Thread-safe via synchronized blocks.
 *
 * With a [store], the queue is restored from the device when created and every
 * change is written back, so queued events survive the app being closed.
 */
internal class EventQueue(private val store: EventQueueStore? = null) {

    /** Maximum number of events to queue */
    private val maxQueueSize = 100

    /** Queued events */
    private val queue = ArrayDeque<EventRequest>()

    init {
        val restored = store?.load().orEmpty().take(maxQueueSize)
        queue.addAll(restored)
        if (restored.isNotEmpty()) {
            LinkFortyLogger.log("Restored ${restored.size} queued event(s)")
        }
    }

    private fun persist() {
        store?.save(queue.toList())
    }

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
        persist()
        LinkFortyLogger.log("Event queued: ${event.eventName} (queue size: ${queue.size})")
        return true
    }

    /** Puts an event back at the head of the queue, keeping its place in line. */
    @Synchronized
    fun requeueFirst(event: EventRequest) {
        queue.addFirst(event)
        while (queue.size > maxQueueSize) queue.removeLast()
        persist()
    }

    /**
     * Dequeues the oldest event.
     * @return the oldest event, or null if queue is empty
     */
    /** The oldest event, left in the queue. */
    @Synchronized
    fun first(): EventRequest? = queue.firstOrNull()

    /** Removes the event with this [clientEventId], once it has been sent or refused. */
    @Synchronized
    fun remove(clientEventId: String) {
        if (queue.removeAll { it.clientEventId == clientEventId }) persist()
    }

    @Synchronized
    fun dequeue(): EventRequest? {
        if (queue.isEmpty()) return null
        return queue.removeFirst().also { persist() }
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
        store?.clear()
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
