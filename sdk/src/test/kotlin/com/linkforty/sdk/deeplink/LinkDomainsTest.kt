package com.linkforty.sdk.deeplink

import android.net.Uri
import com.linkforty.sdk.errors.LinkFortyError
import com.linkforty.sdk.models.DeepLinkData
import com.linkforty.sdk.models.LinkDomainsResponse
import com.linkforty.sdk.models.LinkFortyConfig
import com.linkforty.sdk.network.HttpMethod
import com.linkforty.sdk.network.HttpResponse
import com.linkforty.sdk.network.MockHttpClient
import com.linkforty.sdk.network.NetworkManager
import com.linkforty.sdk.testhelpers.MockFingerprintCollector
import com.linkforty.sdk.testhelpers.MockNetworkManager
import com.linkforty.sdk.testhelpers.MockStorageManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)
class LinkDomainsTest {

    private val known = setOf("acme.toapp.me", "links.acme.com")

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -- Which URLs are looked up --

    @Test
    fun `a web URL on a known link domain is looked up`() {
        assertTrue(LinkDomains.isLinkFortyUrl("https", "links.acme.com", "api.example.com", known))
        assertTrue(LinkDomains.isLinkFortyUrl("http", "acme.toapp.me", "api.example.com", known))
    }

    @Test
    fun `a web URL on the base URL host is looked up`() {
        assertTrue(LinkDomains.isLinkFortyUrl("https", "api.example.com", "api.example.com", known))
    }

    @Test
    fun `a web URL on another host is not looked up once the domains are known`() {
        assertFalse(LinkDomains.isLinkFortyUrl("https", "acme.com", "api.example.com", known))
    }

    @Test
    fun `every web URL is looked up while the domains are unknown`() {
        assertTrue(LinkDomains.isLinkFortyUrl("https", "acme.com", "api.example.com", null))
    }

    @Test
    fun `a custom scheme URL is never looked up`() {
        assertFalse(LinkDomains.isLinkFortyUrl("myapp", "links.acme.com", "api.example.com", known))
        assertFalse(LinkDomains.isLinkFortyUrl("myapp", "product", "api.example.com", null))
    }

    @Test
    fun `hosts compare without case and without a port`() {
        assertTrue(LinkDomains.isLinkFortyUrl("HTTPS", "Links.Acme.COM", "api.example.com", known))
        assertEquals("api.example.com", LinkDomains.hostOf("https://API.example.com:8443/base"))
    }

    // -- Fetching and storing the list --

    @Test
    fun `a fetched list is lower-cased, used and stored`() = runTest {
        val storage = MockStorageManager()
        val domains = LinkDomains(storage, "https://api.example.com")
        val network = MockNetworkManager().apply {
            mockResponse = LinkDomainsResponse(listOf("Go.LinkForty.com", "acme.toapp.me"))
        }

        domains.refresh(network)

        assertEquals("/api/sdk/v1/link-domains", network.lastEndpoint)
        assertEquals(HttpMethod.GET, network.lastMethod)
        assertEquals(setOf("go.linkforty.com", "acme.toapp.me"), domains.known)
        assertEquals(setOf("go.linkforty.com", "acme.toapp.me"), LinkDomains(storage, "https://api.example.com").known)
    }

    @Test
    fun `a stored list is used before any fetch`() {
        val storage = MockStorageManager().apply { savedLinkDomains = "acme.toapp.me\nlinks.acme.com" }

        assertEquals(known, LinkDomains(storage, "https://api.example.com").known)
    }

    @Test
    fun `a 404 or 400 clears the stored list`() = runTest {
        for (status in listOf(404, 400)) {
            val storage = MockStorageManager().apply { savedLinkDomains = "acme.toapp.me" }
            val domains = LinkDomains(storage, "https://api.example.com")
            val network = MockNetworkManager().apply { mockError = LinkFortyError.InvalidResponse(status, "no") }

            domains.refresh(network)

            assertNull(domains.known, "status $status")
            assertNull(storage.savedLinkDomains, "status $status")
        }
    }

    @Test
    fun `a network error or a 5xx keeps the stored list`() = runTest {
        for (error in listOf(LinkFortyError.NetworkError(java.io.IOException("offline")), LinkFortyError.InvalidResponse(503, "busy"))) {
            val storage = MockStorageManager().apply { savedLinkDomains = "acme.toapp.me" }
            val domains = LinkDomains(storage, "https://api.example.com")
            val network = MockNetworkManager().apply { mockError = error }

            domains.refresh(network)

            assertEquals(setOf("acme.toapp.me"), domains.known)
            assertEquals("acme.toapp.me", storage.savedLinkDomains)
        }
    }

    // -- The app token header --

    @Test
    fun `requests carry the app token header when an app token is configured`() = runTest {
        val http = MockHttpClient().apply { mockResponse = HttpResponse(200, """{"domains":[]}""".toByteArray()) }
        val withToken = NetworkManager(LinkFortyConfig(baseURL = "https://api.example.com", appToken = "at_123"), http)
        withToken.request("/api/sdk/v1/link-domains", HttpMethod.GET, null, null, LinkDomainsResponse::class.java)
        assertEquals("at_123", http.lastHeaders?.get("X-LinkForty-App-Token"))

        val withoutToken = NetworkManager(LinkFortyConfig(baseURL = "https://api.example.com"), http)
        withoutToken.request("/api/sdk/v1/link-domains", HttpMethod.GET, null, null, LinkDomainsResponse::class.java)
        assertFalse(http.lastHeaders.orEmpty().containsKey("X-LinkForty-App-Token"))
    }

    // -- The handler --

    private fun uri(scheme: String, host: String, path: List<String>): Uri = mockk<Uri>().also { uri ->
        every { uri.scheme } returns scheme
        every { uri.host } returns host
        every { uri.pathSegments } returns path
        every { uri.queryParameterNames } returns emptySet()
        every { uri.getQueryParameter(any()) } returns null
        every { uri.toString() } returns "$scheme://$host/${path.joinToString("/")}"
    }

    /** Handles [target] with the domains known; returns what handleDeepLink returned, the callback's data and the request count. */
    private fun handle(target: Uri): Triple<Boolean, DeepLinkData?, Int> {
        val storage = MockStorageManager().apply { savedLinkDomains = known.joinToString("\n") }
        val network = MockNetworkManager().apply { mockResponse = DeepLinkData(shortCode = "abc123", linkId = "link-1") }
        val handler = DeepLinkHandler()
        handler.configure(network, MockFingerprintCollector(), "https://api.example.com", null, LinkDomains(storage, "https://api.example.com"))
        val delivered = AtomicReference<DeepLinkData?>(null)
        val latch = CountDownLatch(1)
        handler.onDeepLink { _, data -> delivered.set(data); latch.countDown() }

        val looksUp = handler.handleDeepLink(target)

        assertTrue(latch.await(2, TimeUnit.SECONDS), "the callback receives the URL")
        return Triple(looksUp, delivered.get(), network.requestCount)
    }

    @Test
    fun `a LinkForty link is resolved on the server`() {
        val (looksUp, data, requests) = handle(uri("https", "links.acme.com", listOf("abc123")))

        assertTrue(looksUp)
        assertEquals(1, requests)
        assertEquals("link-1", data?.linkId)
    }

    @Test
    fun `another web URL makes no request and delivers its local parse`() {
        val (looksUp, data, requests) = handle(uri("https", "acme.com", listOf("settings", "abc123")))

        assertFalse(looksUp)
        assertEquals(0, requests)
        assertEquals("abc123", data?.shortCode)
        assertNull(data?.linkId)
    }

    @Test
    fun `a custom scheme URL makes no request and delivers its local parse`() {
        val (looksUp, data, requests) = handle(uri("myapp", "product", listOf("abc123")))

        assertFalse(looksUp)
        assertEquals(0, requests)
        assertNull(data?.linkId)
    }
}
