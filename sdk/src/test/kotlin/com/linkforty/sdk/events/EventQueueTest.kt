package com.linkforty.sdk.events

import com.linkforty.sdk.models.EventRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EventQueueTest {

    private lateinit var sut: EventQueue

    @BeforeEach
    fun setUp() {
        sut = EventQueue()
    }

    private fun createEvent(name: String = "test"): EventRequest {
        return EventRequest(
            installId = "install-123",
            eventName = name,
            eventData = emptyMap()
        )
    }

    // -- Enqueue Tests --

    @Test
    fun `enqueue adds event to queue`() {
        val result = sut.enqueue(createEvent())

        assertTrue(result)
        assertEquals(1, sut.count)
    }

    @Test
    fun `enqueue returns false when queue is full`() {
        // Fill queue to max (100)
        repeat(100) { sut.enqueue(createEvent("event-$it")) }

        assertEquals(100, sut.count)
        assertTrue(sut.isFull)

        // 101st should fail
        val result = sut.enqueue(createEvent("overflow"))
        assertFalse(result)
        assertEquals(100, sut.count)
    }

    // -- Dequeue Tests --

    @Test
    fun `dequeue returns oldest event (FIFO)`() {
        sut.enqueue(createEvent("first"))
        sut.enqueue(createEvent("second"))
        sut.enqueue(createEvent("third"))

        val event = sut.dequeue()
        assertEquals("first", event?.eventName)
        assertEquals(2, sut.count)
    }

    @Test
    fun `dequeue returns null when empty`() {
        assertNull(sut.dequeue())
    }

    // -- Peek Tests --

    @Test
    fun `peek returns all events without removing`() {
        sut.enqueue(createEvent("a"))
        sut.enqueue(createEvent("b"))

        val events = sut.peek()
        assertEquals(2, events.size)
        assertEquals("a", events[0].eventName)
        assertEquals("b", events[1].eventName)

        // Queue should still be full
        assertEquals(2, sut.count)
    }

    @Test
    fun `peek returns empty list when empty`() {
        assertTrue(sut.peek().isEmpty())
    }

    // -- Clear Tests --

    @Test
    fun `clear removes all events`() {
        sut.enqueue(createEvent("a"))
        sut.enqueue(createEvent("b"))
        sut.enqueue(createEvent("c"))

        sut.clear()

        assertEquals(0, sut.count)
        assertTrue(sut.isEmpty)
    }

    @Test
    fun `clear on empty queue does not crash`() {
        sut.clear()
        assertEquals(0, sut.count)
    }

    // -- Property Tests --

    @Test
    fun `isEmpty returns true when empty`() {
        assertTrue(sut.isEmpty)
    }

    @Test
    fun `isEmpty returns false when has events`() {
        sut.enqueue(createEvent())
        assertFalse(sut.isEmpty)
    }

    @Test
    fun `isFull returns false when not full`() {
        assertFalse(sut.isFull)
    }

    @Test
    fun `count tracks queue size`() {
        assertEquals(0, sut.count)

        sut.enqueue(createEvent())
        assertEquals(1, sut.count)

        sut.enqueue(createEvent())
        assertEquals(2, sut.count)

        sut.dequeue()
        assertEquals(1, sut.count)
    }

    // -- Thread Safety Tests --

    @Test
    fun `concurrent enqueue and dequeue do not crash`() {
        val threads = mutableListOf<Thread>()

        // Enqueue from multiple threads
        repeat(10) { i ->
            threads.add(Thread {
                repeat(10) { j ->
                    sut.enqueue(createEvent("thread-$i-event-$j"))
                }
            })
        }

        // Dequeue from multiple threads
        repeat(5) {
            threads.add(Thread {
                repeat(20) {
                    sut.dequeue()
                }
            })
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // Should not crash; count should be non-negative
        assertTrue(sut.count >= 0)
    }
}
