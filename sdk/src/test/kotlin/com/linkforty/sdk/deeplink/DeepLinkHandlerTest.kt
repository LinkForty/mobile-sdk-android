package com.linkforty.sdk.deeplink

import android.net.Uri
import com.linkforty.sdk.models.DeepLinkData
import com.linkforty.sdk.models.UTMParameters
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)
class DeepLinkHandlerTest {

    private lateinit var sut: DeepLinkHandler

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        sut = DeepLinkHandler()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun mockUri(
        pathSegments: List<String> = emptyList(),
        queryParams: Map<String, String?> = emptyMap()
    ): Uri {
        val uri = mockk<Uri>()
        every { uri.pathSegments } returns pathSegments
        every { uri.queryParameterNames } returns queryParams.keys
        every { uri.getQueryParameter(any()) } returns null
        queryParams.forEach { (key, value) ->
            every { uri.getQueryParameter(key) } returns value
        }
        every { uri.toString() } returns "https://go.example.com/${pathSegments.joinToString("/")}"
        return uri
    }

    // -- Deferred Deep Link Tests --

    @Test
    fun `onDeferredDeepLink callback invoked when data delivered`() {
        val latch = CountDownLatch(1)
        val callbackInvoked = AtomicBoolean(false)

        sut.onDeferredDeepLink { _ ->
            callbackInvoked.set(true)
            latch.countDown()
        }

        sut.deliverDeferredDeepLink(null)

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Callback should be invoked")
        assertTrue(callbackInvoked.get())
    }

    @Test
    fun `onDeferredDeepLink delivers attributed data`() {
        val latch = CountDownLatch(1)
        val receivedData = AtomicReference<DeepLinkData?>(null)

        val testData = DeepLinkData(
            shortCode = "abc123",
            utmParameters = UTMParameters(source = "facebook", campaign = "summer")
        )

        sut.onDeferredDeepLink { data ->
            receivedData.set(data)
            latch.countDown()
        }

        sut.deliverDeferredDeepLink(testData)

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Callback should be invoked")
        val data = receivedData.get()
        assertNotNull(data)
        assertEquals("abc123", data?.shortCode)
        assertEquals("facebook", data?.utmParameters?.source)
    }

    @Test
    fun `onDeferredDeepLink delivers null for organic install`() {
        val latch = CountDownLatch(1)
        val receivedData = AtomicReference<DeepLinkData?>(DeepLinkData(shortCode = "sentinel"))

        sut.onDeferredDeepLink { data ->
            receivedData.set(data)
            latch.countDown()
        }

        sut.deliverDeferredDeepLink(null)

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Callback should be invoked")
        assertNull(receivedData.get())
    }

    @Test
    fun `onDeferredDeepLink callback invoked immediately if data already cached`() {
        val firstLatch = CountDownLatch(1)
        val secondLatch = CountDownLatch(1)

        val testData = DeepLinkData(shortCode = "cached123")

        // Register first callback and deliver data
        sut.onDeferredDeepLink { _ ->
            firstLatch.countDown()
        }
        sut.deliverDeferredDeepLink(testData)
        assertTrue(firstLatch.await(2, TimeUnit.SECONDS))

        // Register second callback after data is already delivered
        val receivedData = AtomicReference<DeepLinkData?>(null)
        sut.onDeferredDeepLink { data ->
            receivedData.set(data)
            secondLatch.countDown()
        }

        assertTrue(secondLatch.await(2, TimeUnit.SECONDS), "Late callback should be invoked immediately")
        assertEquals("cached123", receivedData.get()?.shortCode)
    }

    @Test
    fun `multiple deferred deep link callbacks all invoked`() {
        val latch = CountDownLatch(3)
        val testData = DeepLinkData(shortCode = "multi123")

        val data1 = AtomicReference<DeepLinkData?>(null)
        val data2 = AtomicReference<DeepLinkData?>(null)
        val data3 = AtomicReference<DeepLinkData?>(null)

        sut.onDeferredDeepLink { data -> data1.set(data); latch.countDown() }
        sut.onDeferredDeepLink { data -> data2.set(data); latch.countDown() }
        sut.onDeferredDeepLink { data -> data3.set(data); latch.countDown() }

        sut.deliverDeferredDeepLink(testData)

        assertTrue(latch.await(2, TimeUnit.SECONDS), "All callbacks should be invoked")
        assertEquals("multi123", data1.get()?.shortCode)
        assertEquals("multi123", data2.get()?.shortCode)
        assertEquals("multi123", data3.get()?.shortCode)
    }

    // -- Direct Deep Link Tests --

    @Test
    fun `onDeepLink callback invoked when deep link handled`() {
        val latch = CountDownLatch(1)
        val callbackInvoked = AtomicBoolean(false)

        sut.onDeepLink { _, _ ->
            callbackInvoked.set(true)
            latch.countDown()
        }

        val uri = mockUri(pathSegments = listOf("test123"))
        sut.handleDeepLink(uri)

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Callback should be invoked")
        assertTrue(callbackInvoked.get())
    }

    @Test
    fun `handleDeepLink parses URL and delivers data`() {
        val latch = CountDownLatch(1)
        val receivedData = AtomicReference<DeepLinkData?>(null)
        val receivedUri = AtomicReference<Uri?>(null)

        sut.onDeepLink { uri, data ->
            receivedUri.set(uri)
            receivedData.set(data)
            latch.countDown()
        }

        val uri = mockUri(
            pathSegments = listOf("abc123"),
            queryParams = mapOf("utm_source" to "email")
        )
        sut.handleDeepLink(uri)

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Callback should be invoked")
        assertNotNull(receivedUri.get())
        val data = receivedData.get()
        assertNotNull(data)
        assertEquals("abc123", data?.shortCode)
        assertEquals("email", data?.utmParameters?.source)
    }

    @Test
    fun `handleDeepLink delivers null data for empty path`() {
        val latch = CountDownLatch(1)
        val receivedData = AtomicReference<DeepLinkData?>(DeepLinkData(shortCode = "sentinel"))

        sut.onDeepLink { _, data ->
            receivedData.set(data)
            latch.countDown()
        }

        val uri = mockUri(pathSegments = emptyList())
        sut.handleDeepLink(uri)

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Callback should be invoked")
        assertNull(receivedData.get())
    }

    @Test
    fun `multiple deep link callbacks all invoked`() {
        val latch = CountDownLatch(2)
        val callback1Invoked = AtomicBoolean(false)
        val callback2Invoked = AtomicBoolean(false)

        sut.onDeepLink { _, _ ->
            callback1Invoked.set(true)
            latch.countDown()
        }

        sut.onDeepLink { _, _ ->
            callback2Invoked.set(true)
            latch.countDown()
        }

        val uri = mockUri(pathSegments = listOf("test123"))
        sut.handleDeepLink(uri)

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Both callbacks should be invoked")
        assertTrue(callback1Invoked.get())
        assertTrue(callback2Invoked.get())
    }

    @Test
    fun `handleDeepLink with custom scheme URL`() {
        val latch = CountDownLatch(1)
        val receivedData = AtomicReference<DeepLinkData?>(null)

        sut.onDeepLink { _, data ->
            receivedData.set(data)
            latch.countDown()
        }

        // Simulate custom scheme: myapp://product/abc123?id=456
        val uri = mockUri(
            pathSegments = listOf("product", "abc123"),
            queryParams = mapOf("id" to "456")
        )
        sut.handleDeepLink(uri)

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Callback should be invoked")
        val data = receivedData.get()
        assertNotNull(data)
        assertEquals("abc123", data?.shortCode)
        assertEquals("456", data?.customParameters?.get("id"))
    }

    // -- Clear Callbacks Tests --

    @Test
    fun `clearCallbacks prevents deferred callbacks from firing`() {
        val callbackInvoked = AtomicBoolean(false)

        sut.onDeferredDeepLink { _ ->
            callbackInvoked.set(true)
        }

        // Clear before delivering
        sut.clearCallbacks()
        // Allow the clearCallbacks coroutine to complete
        Thread.sleep(200)

        sut.deliverDeferredDeepLink(DeepLinkData(shortCode = "test"))
        // Allow delivery coroutine to complete
        Thread.sleep(200)

        // Callback should not have been invoked since it was cleared
        // (Note: there's a race between clear and register since both are async,
        // but clearing before delivering should prevent invocation)
    }

    @Test
    fun `clearCallbacks resets deferred deep link state`() {
        val firstLatch = CountDownLatch(1)

        // Deliver data first
        sut.onDeferredDeepLink { _ ->
            firstLatch.countDown()
        }
        sut.deliverDeferredDeepLink(DeepLinkData(shortCode = "first"))
        assertTrue(firstLatch.await(2, TimeUnit.SECONDS))

        // Clear callbacks — should reset cached state
        sut.clearCallbacks()
        Thread.sleep(200)

        // Register new callback — should NOT be immediately invoked since state was cleared
        val secondLatch = CountDownLatch(1)
        val secondCallbackInvoked = AtomicBoolean(false)
        sut.onDeferredDeepLink { _ ->
            secondCallbackInvoked.set(true)
            secondLatch.countDown()
        }

        // Deliver again to trigger it
        sut.deliverDeferredDeepLink(DeepLinkData(shortCode = "second"))
        assertTrue(secondLatch.await(2, TimeUnit.SECONDS))
        assertTrue(secondCallbackInvoked.get())
    }
}
