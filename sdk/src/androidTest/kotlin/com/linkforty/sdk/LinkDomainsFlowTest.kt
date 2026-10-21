package com.linkforty.sdk

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.linkforty.sdk.models.DeepLinkData
import com.linkforty.sdk.models.LinkFortyConfig
import com.linkforty.sdk.storage.StorageKeys
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.concurrent.TimeUnit

/** Which URLs the SDK looks up, once it knows the workspace's link domains. */
@RunWith(AndroidJUnit4::class)
class LinkDomainsFlowTest {

    private lateinit var server: MockWebServer
    private val paths: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val appTokens: MutableList<String?> = Collections.synchronizedList(mutableListOf())
    private var domainsDelayMs = 0L
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        clearStoredData()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore('?')
                paths.add(path)
                appTokens.add(request.getHeader("X-LinkForty-App-Token"))
                return when {
                    path == "/api/sdk/v1/install" -> json("""{"installId":"install-1","attributed":false,"confidenceScore":0,"matchedFactors":[],"deepLinkData":null}""")
                    path == "/api/sdk/v1/link-domains" ->
                        json("""{"domains":["Links.Acme.com"]}""").setBodyDelay(domainsDelayMs, TimeUnit.MILLISECONDS)
                    path.startsWith("/api/sdk/v1/resolve/") -> json("""{"shortCode":"abc123","linkId":"link-1"}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        clearStoredData()
        server.shutdown()
    }

    private fun clearStoredData() {
        runCatching { LinkForty.shared.reset() }
        context.getSharedPreferences(StorageKeys.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private fun config() = LinkFortyConfig(baseURL = server.url("/").toString().trimEnd('/'), appToken = "at_test")

    private fun resolves() = paths.count { it.startsWith("/api/sdk/v1/resolve/") }

    private suspend fun waitFor(timeoutMs: Long = 5000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) delay(50)
    }

    @Test
    fun onlyUrlsOnTheWorkspaceLinkDomainsAreLookedUp() = runBlocking {
        val opened = Collections.synchronizedList(mutableListOf<DeepLinkData?>())
        LinkForty.shared.onDeepLink { _, data -> opened.add(data) }
        LinkForty.initialize(context, config())
        waitFor { "/api/sdk/v1/link-domains" in paths }
        delay(300)

        val other = LinkForty.shared.handleDeepLink(Uri.parse("https://acme.com/settings/abc123"))
        val custom = LinkForty.shared.handleDeepLink(Uri.parse("myapp://product/abc123"))
        waitFor { opened.size == 2 }
        delay(500)

        assertFalse(other)
        assertFalse(custom)
        assertEquals(0, resolves())
        assertTrue(opened.all { it?.linkId == null })

        val link = LinkForty.shared.handleDeepLink(Uri.parse("https://links.acme.com/abc123"))
        waitFor { opened.size == 3 }

        assertTrue(link)
        assertEquals(1, resolves())
        assertEquals("link-1", opened.last()?.linkId)
        assertTrue("every request names the app", appTokens.all { it == "at_test" })
    }

    @Test
    fun aLinkHeldBeforeInitializeWaitsForTheDomainFetch() = runBlocking {
        domainsDelayMs = 1000
        val opened = Collections.synchronizedList(mutableListOf<DeepLinkData?>())
        LinkForty.shared.onDeepLink { _, data -> opened.add(data) }

        val held = LinkForty.shared.handleDeepLink(Uri.parse("https://acme.com/settings/abc123"))
        LinkForty.initialize(context, config())
        waitFor { opened.isNotEmpty() }
        delay(500)

        assertTrue("a held web URL reports true", held)
        assertEquals(1, opened.size)
        assertNull(opened.single()?.linkId)
        assertEquals("decided against the fetched domains, not looked up", 0, resolves())
    }
}
