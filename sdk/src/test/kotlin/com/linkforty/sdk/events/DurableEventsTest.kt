package com.linkforty.sdk.events

import com.linkforty.sdk.attribution.AttributionContext
import com.linkforty.sdk.errors.LinkFortyError
import com.linkforty.sdk.models.EventRequest
import com.linkforty.sdk.models.LinkFortyConfig
import com.linkforty.sdk.network.HttpResponse
import com.linkforty.sdk.network.MockHttpClient
import com.linkforty.sdk.network.NetworkManager
import com.linkforty.sdk.testhelpers.MockStorageManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Events kept on the device until the server has them, and sent once each. */
class DurableEventsTest {

    private companion object {
        val ACKNOWLEDGED = """{"eventId": "evt-1", "acknowledged": true}""".toByteArray()
    }

    private lateinit var http: RecordingHttpClient
    private lateinit var storage: MockStorageManager
    private lateinit var networkManager: NetworkManager

    @BeforeEach
    fun setUp() {
        http = RecordingHttpClient()
        storage = MockStorageManager().apply { mockInstallId = "install-1" }
        networkManager = NetworkManager(LinkFortyConfig(baseURL = "https://api.example.com"), http)
    }

    /** A tracker over a queue stored in [storage], as the SDK builds it. */
    private fun tracker(): Pair<EventTracker, EventQueue> {
        val queue = EventQueue(EventQueueStore(storage))
        return EventTracker(networkManager, storage, AttributionContext(storage), queue) to queue
    }

    @Test
    fun `every event carries a clientEventId, and a retry carries the same one`() = runTest {
        val (sut, queue) = tracker()
        http.error = java.io.IOException("offline")
        sut.trackEvent("purchase")
        val firstAttempt = http.sentClientEventIds.last()

        http.error = null
        http.response = HttpResponse(200, ACKNOWLEDGED)
        sut.flushQueue()

        assertTrue(firstAttempt.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
        assertEquals(firstAttempt, http.sentClientEventIds.last())
        assertEquals(0, queue.count)
    }

    @Test
    fun `two events get different clientEventIds`() = runTest {
        val (sut, _) = tracker()
        http.response = HttpResponse(200, ACKNOWLEDGED)

        sut.trackEvent("a")
        sut.trackEvent("b")

        assertEquals(2, http.sentClientEventIds.toSet().size)
    }

    @Test
    fun `a queued event is restored by a new tracker over the same storage`() = runTest {
        val (offline, _) = tracker()
        http.error = java.io.IOException("offline")
        offline.trackEvent("purchase", mapOf("count" to 3, "price" to 9.99, "nested" to mapOf("tags" to listOf("a", 2))))
        val queuedId = http.sentClientEventIds.last()

        // A later launch: a new queue over the same stored copy.
        http.error = null
        http.response = HttpResponse(200, ACKNOWLEDGED)
        http.sentBodies.clear()
        val (relaunched, queue) = tracker()
        assertEquals(1, queue.count)
        relaunched.flushQueue()

        val body = http.sentBodies.single()
        val sent = parseJson(body)
        assertEquals(queuedId, sent["clientEventId"])
        assertEquals("purchase", sent["eventName"])
        // Whole numbers go back out as integers, as they were tracked.
        assertTrue(body.contains("\"count\":3,") || body.contains("\"count\":3}"), body)
        assertTrue(body.contains("\"price\":9.99"), body)
        assertTrue(body.contains("\"tags\":[\"a\",2]"), body)
        assertNull(storage.savedEventQueue)
    }

    @Test
    fun `an event is stored before it is sent`() = runTest {
        val (sut, _) = tracker()
        http.onRequest = { assertNotNull(storage.savedEventQueue, "queue stored while the send is in flight") }
        http.response = HttpResponse(200, ACKNOWLEDGED)

        sut.trackEvent("purchase")

        assertNull(storage.savedEventQueue)
    }

    @Test
    fun `a rejected event throws and is not kept`() = runTest {
        val (sut, queue) = tracker()
        http.response = HttpResponse(404, "Install not found".toByteArray())

        assertThrows<LinkFortyError.InvalidResponse> { sut.trackEvent("purchase") }

        assertEquals(0, queue.count)
        assertNull(storage.savedEventQueue)
    }

    @Test
    fun `clearing the queue clears the stored copy`() = runTest {
        val (sut, queue) = tracker()
        http.error = java.io.IOException("offline")
        sut.trackEvent("purchase")
        assertNotNull(storage.savedEventQueue)

        queue.clear()

        assertNull(storage.savedEventQueue)
        assertEquals(0, tracker().second.count)
    }

    @Test
    fun `concurrent flushes send each queued event once`() = runTest {
        val (sut, queue) = tracker()
        http.error = java.io.IOException("offline")
        repeat(3) { sut.trackEvent("e$it") }
        assertEquals(3, queue.count)

        http.error = null
        http.response = HttpResponse(200, ACKNOWLEDGED)
        http.sentClientEventIds.clear()
        (1..4).map { async { sut.flushQueue() } }.awaitAll()

        assertEquals(3, http.sentClientEventIds.size)
        assertEquals(3, http.sentClientEventIds.toSet().size)
        assertEquals(0, queue.count)
    }

    @Test
    fun `an unreadable stored queue is discarded`() {
        storage.savedEventQueue = "not json"

        val queue = EventQueue(EventQueueStore(storage))

        assertEquals(0, queue.count)
        assertNull(storage.savedEventQueue)
    }

    @Test
    fun `a queued event keeps its clientEventId through the stored copy`() {
        val event = EventRequest(installId = "install-1", eventName = "e", eventData = emptyMap())
        EventQueue(EventQueueStore(storage)).enqueue(event)

        assertEquals(event.clientEventId, EventQueue(EventQueueStore(storage)).first()?.clientEventId)
    }
}

/** Answers with [response] or throws [error]; records the event bodies and their clientEventIds. */
internal class RecordingHttpClient : com.linkforty.sdk.network.HttpClient {
    var response: HttpResponse? = null
    var error: Exception? = null
    var onRequest: (() -> Unit)? = null
    val sentBodies = mutableListOf<String>()
    val sentClientEventIds = mutableListOf<String>()

    override suspend fun execute(
        url: String,
        method: com.linkforty.sdk.network.HttpMethod,
        body: ByteArray?,
        headers: Map<String, String>
    ): HttpResponse {
        body?.let {
            val json = String(it)
            sentBodies.add(json)
            (parseJson(json)["clientEventId"] as? String)?.let(sentClientEventIds::add)
        }
        onRequest?.invoke()
        error?.let { throw it }
        return response ?: throw RuntimeException("No response configured")
    }
}

private val jsonMapAdapter = Moshi.Builder().build()
    .adapter<Map<String, Any?>>(Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))

internal fun parseJson(json: String): Map<String, Any?> = jsonMapAdapter.fromJson(json).orEmpty()
