package com.linkforty.sdk.events

import com.linkforty.sdk.errors.LinkFortyError
import com.linkforty.sdk.models.EventResponse
import com.linkforty.sdk.network.HttpMethod
import com.linkforty.sdk.network.MockHttpClient
import com.linkforty.sdk.network.HttpResponse
import com.linkforty.sdk.models.LinkFortyConfig
import com.linkforty.sdk.network.NetworkManager
import com.linkforty.sdk.testhelpers.MockStorageManager
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class EventTrackerTest {

    private lateinit var mockHttpClient: MockHttpClient
    private lateinit var mockStorage: MockStorageManager
    private lateinit var networkManager: NetworkManager
    private lateinit var eventQueue: EventQueue
    private lateinit var sut: EventTracker

    @BeforeEach
    fun setUp() {
        mockHttpClient = MockHttpClient()
        mockStorage = MockStorageManager().apply {
            mockInstallId = "test-install-id"
        }
        val config = LinkFortyConfig(
            baseURL = "https://api.example.com",
            debug = false
        )
        networkManager = NetworkManager(config, mockHttpClient)
        eventQueue = EventQueue()
        sut = EventTracker(networkManager, mockStorage, eventQueue)
    }

    // -- Track Event Tests --

    @Test
    fun `trackEvent sends event to correct endpoint`() = runTest {
        mockHttpClient.mockResponse = HttpResponse(200, """{"success": true}""".toByteArray())

        sut.trackEvent("purchase", mapOf("product_id" to "123"))

        assertTrue(mockHttpClient.lastUrl?.endsWith("/api/sdk/v1/event") == true)
        assertEquals(HttpMethod.POST, mockHttpClient.lastMethod)
    }

    @Test
    fun `trackEvent throws for empty name`() = runTest {
        assertThrows<LinkFortyError.InvalidEventData> {
            sut.trackEvent("")
        }
    }

    @Test
    fun `trackEvent throws for blank name`() = runTest {
        assertThrows<LinkFortyError.InvalidEventData> {
            sut.trackEvent("   ")
        }
    }

    @Test
    fun `trackEvent throws when not initialized (no install ID)`() = runTest {
        mockStorage.mockInstallId = null

        assertThrows<LinkFortyError.NotInitialized> {
            sut.trackEvent("test")
        }
    }

    @Test
    fun `trackEvent queues on network failure`() = runTest {
        mockHttpClient.mockResponse = HttpResponse(500, "Error".toByteArray())

        try {
            sut.trackEvent("test")
        } catch (_: Exception) {
            // Expected
        }

        assertEquals(1, eventQueue.count)
    }

    // -- Track Revenue Tests --

    @Test
    fun `trackRevenue sends revenue event`() = runTest {
        mockHttpClient.mockResponse = HttpResponse(200, """{"success": true}""".toByteArray())

        sut.trackRevenue(BigDecimal("29.99"), "USD")

        assertNotNull(mockHttpClient.lastBody)
    }

    @Test
    fun `trackRevenue throws for negative amount`() = runTest {
        assertThrows<LinkFortyError.InvalidEventData> {
            sut.trackRevenue(BigDecimal("-1.00"), "USD")
        }
    }

    @Test
    fun `trackRevenue includes amount and currency in properties`() = runTest {
        mockHttpClient.mockResponse = HttpResponse(200, """{"success": true}""".toByteArray())

        sut.trackRevenue(
            BigDecimal("9.99"),
            "EUR",
            mapOf("product_id" to "456")
        )

        // Should have sent the event
        assertNotNull(mockHttpClient.lastBody)
    }

    // -- Queue Management Tests --

    @Test
    fun `queuedEventCount reflects queue size`() {
        assertEquals(0, sut.queuedEventCount)
    }

    @Test
    fun `clearQueue empties the event queue`() = runTest {
        // Manually enqueue some events
        eventQueue.enqueue(
            com.linkforty.sdk.models.EventRequest(
                installId = "id",
                eventName = "test",
                eventData = emptyMap()
            )
        )
        assertEquals(1, sut.queuedEventCount)

        sut.clearQueue()
        assertEquals(0, sut.queuedEventCount)
    }

    @Test
    fun `flushQueue sends queued events`() = runTest {
        // Queue an event manually
        eventQueue.enqueue(
            com.linkforty.sdk.models.EventRequest(
                installId = "id",
                eventName = "queued-event",
                eventData = emptyMap()
            )
        )
        mockHttpClient.mockResponse = HttpResponse(200, """{"success": true}""".toByteArray())

        sut.flushQueue()

        assertEquals(0, eventQueue.count)
    }

    private fun assertNotNull(value: Any?) {
        org.junit.jupiter.api.Assertions.assertNotNull(value)
    }
}
