package com.linkforty.sdk.deeplink

import android.net.Uri
import com.linkforty.sdk.errors.LinkFortyError
import com.linkforty.sdk.models.DeepLinkData
import com.linkforty.sdk.testhelpers.MockFingerprintCollector
import com.linkforty.sdk.testhelpers.MockNetworkManager
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
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)
class DeepLinkServerResolutionTest {

    private lateinit var mockNetwork: MockNetworkManager
    private lateinit var mockFingerprint: MockFingerprintCollector
    private lateinit var sut: DeepLinkHandler

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockNetwork = MockNetworkManager()
        mockFingerprint = MockFingerprintCollector()

        sut = DeepLinkHandler()
        sut.configure(
            networkManager = mockNetwork,
            fingerprintCollector = mockFingerprint,
            baseURL = "https://go.example.com"
        )
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

    // -- Server-Side Resolution Tests --

    @Test
    fun `handleDeepLink resolves via server and delivers enriched data`() {
        val latch = CountDownLatch(1)
        val receivedData = AtomicReference<DeepLinkData?>(null)

        val enrichedData = DeepLinkData(
            shortCode = "abc123",
            deepLinkPath = "/product/456",
            appScheme = "myapp",
            linkId = "link-uuid-1"
        )
        mockNetwork.mockResponse = enrichedData

        sut.onDeepLink { _, data ->
            receivedData.set(data)
            latch.countDown()
        }

        val uri = mockUri(pathSegments = listOf("abc123"))
        sut.handleDeepLink(uri)

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Callback should be invoked")
        val data = receivedData.get()
        assertNotNull(data)
        assertEquals("abc123", data?.shortCode)
        assertEquals("/product/456", data?.deepLinkPath)
        assertEquals("myapp", data?.appScheme)
        assertEquals("link-uuid-1", data?.linkId)
    }

    @Test
    fun `handleDeepLink sends resolve request to correct endpoint`() {
        val latch = CountDownLatch(1)

        mockNetwork.mockResponse = DeepLinkData(shortCode = "abc123")

        sut.onDeepLink { _, _ ->
            latch.countDown()
        }

        val uri = mockUri(pathSegments = listOf("abc123"))
        sut.handleDeepLink(uri)

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Callback should be invoked")
        val endpoint = mockNetwork.lastEndpoint ?: ""
        assertTrue(endpoint.contains("/api/sdk/v1/resolve/"), "Endpoint should contain resolve path")
        assertTrue(endpoint.contains("abc123"), "Endpoint should contain short code")
    }

    @Test
    fun `handleDeepLink with template slug resolves to correct endpoint`() {
        val latch = CountDownLatch(1)

        mockNetwork.mockResponse = DeepLinkData(shortCode = "xyz789", deepLinkPath = "/product/789")

        sut.onDeepLink { _, _ ->
            latch.countDown()
        }

        val uri = mockUri(pathSegments = listOf("tmpl", "xyz789"))
        sut.handleDeepLink(uri)

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Callback should be invoked")
        val endpoint = mockNetwork.lastEndpoint ?: ""
        assertTrue(
            endpoint.startsWith("/api/sdk/v1/resolve/tmpl/xyz789"),
            "Endpoint should be /api/sdk/v1/resolve/tmpl/xyz789, got: $endpoint"
        )
    }

    @Test
    fun `handleDeepLink falls back to local parse on server error`() {
        val latch = CountDownLatch(1)
        val receivedData = AtomicReference<DeepLinkData?>(null)

        mockNetwork.mockError = LinkFortyError.NetworkError(RuntimeException("Connection failed"))

        sut.onDeepLink { _, data ->
            receivedData.set(data)
            latch.countDown()
        }

        val uri = mockUri(
            pathSegments = listOf("fallback123"),
            queryParams = mapOf("utm_source" to "test")
        )
        sut.handleDeepLink(uri)

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Callback should be invoked")
        val data = receivedData.get()
        assertNotNull(data, "Should fall back to local parse")
        assertEquals("fallback123", data?.shortCode)
        assertEquals("test", data?.utmParameters?.source)
    }

    @Test
    fun `handleDeepLink without configuration uses local parse`() {
        val latch = CountDownLatch(1)
        val receivedData = AtomicReference<DeepLinkData?>(null)

        // Create a new handler without configure()
        val unconfiguredHandler = DeepLinkHandler()

        unconfiguredHandler.onDeepLink { _, data ->
            receivedData.set(data)
            latch.countDown()
        }

        val uri = mockUri(
            pathSegments = listOf("local123"),
            queryParams = mapOf("utm_campaign" to "summer")
        )
        unconfiguredHandler.handleDeepLink(uri)

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Callback should be invoked")
        val data = receivedData.get()
        assertNotNull(data, "Should use local parse when not configured")
        assertEquals("local123", data?.shortCode)
        assertEquals("summer", data?.utmParameters?.campaign)
    }

    @Test
    fun `handleDeepLink sends fingerprint parameters in resolve request`() {
        val latch = CountDownLatch(1)

        mockNetwork.mockResponse = DeepLinkData(shortCode = "fp123")

        sut.onDeepLink { _, _ ->
            latch.countDown()
        }

        val uri = mockUri(pathSegments = listOf("fp123"))
        sut.handleDeepLink(uri)

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Callback should be invoked")

        val endpoint = mockNetwork.lastEndpoint ?: ""
        assertTrue(endpoint.contains("fp_tz="), "Endpoint should contain fp_tz, got: $endpoint")
        assertTrue(endpoint.contains("fp_lang="), "Endpoint should contain fp_lang, got: $endpoint")
        assertTrue(endpoint.contains("fp_sw="), "Endpoint should contain fp_sw, got: $endpoint")
        assertTrue(endpoint.contains("fp_sh="), "Endpoint should contain fp_sh, got: $endpoint")
        assertTrue(endpoint.contains("fp_platform="), "Endpoint should contain fp_platform, got: $endpoint")
        assertTrue(endpoint.contains("fp_pv="), "Endpoint should contain fp_pv, got: $endpoint")

        // Verify fingerprint was actually collected
        assertTrue(mockFingerprint.collectCalled, "Fingerprint should have been collected")
    }

    @Test
    fun `handleDeepLink with empty path delivers null data`() {
        val latch = CountDownLatch(1)
        val receivedData = AtomicReference<DeepLinkData?>(DeepLinkData(shortCode = "sentinel"))

        sut.onDeepLink { _, data ->
            receivedData.set(data)
            latch.countDown()
        }

        val uri = mockUri(pathSegments = emptyList())
        sut.handleDeepLink(uri)

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Callback should be invoked")
        assertNull(receivedData.get(), "Empty path should result in null data")
    }
}
