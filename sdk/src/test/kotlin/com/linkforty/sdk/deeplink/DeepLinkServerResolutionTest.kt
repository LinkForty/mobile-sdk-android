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
        queryParams: Map<String, String?> = emptyMap(),
        scheme: String = "https",
        host: String = "go.example.com"
    ): Uri {
        val uri = mockk<Uri>()
        every { uri.scheme } returns scheme
        every { uri.host } returns host
        every { uri.pathSegments } returns pathSegments
        every { uri.queryParameterNames } returns queryParams.keys
        every { uri.getQueryParameter(any()) } returns null
        queryParams.forEach { (key, value) ->
            every { uri.getQueryParameter(key) } returns value
        }
        every { uri.toString() } returns "https://go.example.com/${pathSegments.joinToString("/")}"
        return uri
    }

    // -- Resolve request query --

    /** Handles a link with [queryParams] and returns the resolve endpoint requested. */
    private fun resolveEndpointFor(
        queryParams: Map<String, String?>,
        pathSegments: List<String> = listOf("abc123")
    ): String {
        val latch = CountDownLatch(1)
        mockNetwork.mockResponse = DeepLinkData(shortCode = "abc123", linkId = "link-uuid-1")
        sut.onDeepLink { _, _ -> latch.countDown() }
        sut.handleDeepLink(mockUri(pathSegments = pathSegments, queryParams = queryParams))
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Callback should be invoked")
        return mockNetwork.lastEndpoint ?: ""
    }

    /** The decoded query of an endpoint, as a server would read it. */
    private fun queryOf(endpoint: String): Map<String, String> =
        endpoint.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.associate { pair ->
            val key = java.net.URLDecoder.decode(pair.substringBefore('='), "UTF-8")
            val value = java.net.URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8")
            key to value
        }

    @Test
    fun `resolve passes the link's query parameters with the fingerprint`() {
        val endpoint = resolveEndpointFor(mapOf("os_id" to "u1", "utm_campaign" to "winback"))

        assertTrue(endpoint.startsWith("/api/sdk/v1/resolve/abc123?"), endpoint)
        assertEquals(
            mapOf(
                "os_id" to "u1",
                "utm_campaign" to "winback",
                "fp_tz" to "America/New_York",
                "fp_lang" to "en-US",
                "fp_sw" to "1080",
                "fp_sh" to "2400",
                "fp_platform" to "Android",
                "fp_pv" to "13"
            ),
            queryOf(endpoint)
        )
    }

    @Test
    fun `resolve keeps its own fingerprint over fp_ parameters on the link`() {
        val query = queryOf(resolveEndpointFor(mapOf("fp_tz" to "evil", "FP_LANG" to "evil", "os_id" to "u1")))

        assertEquals("America/New_York", query["fp_tz"])
        assertEquals("en-US", query["fp_lang"])
        assertNull(query["FP_LANG"])
        assertEquals("u1", query["os_id"])
    }

    @Test
    fun `resolve encodes values once`() {
        val query = queryOf(resolveEndpointFor(mapOf("note" to "a b&c", "email" to "a+b@example.com")))

        assertEquals("a b&c", query["note"])
        assertEquals("a+b@example.com", query["email"])
    }

    @Test
    fun `resolve skips empty link parameters`() {
        val query = queryOf(resolveEndpointFor(mapOf("os_id" to "", "flag" to null, "ct_id" to "c1")))

        assertNull(query["os_id"])
        assertNull(query["flag"])
        assertEquals("c1", query["ct_id"])
    }

    @Test
    fun `resolve sends only the fingerprint for a link with no query`() {
        val query = queryOf(resolveEndpointFor(emptyMap()))

        assertEquals(listOf("fp_lang", "fp_platform", "fp_pv", "fp_sh", "fp_sw", "fp_tz"), query.keys.sorted())
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
