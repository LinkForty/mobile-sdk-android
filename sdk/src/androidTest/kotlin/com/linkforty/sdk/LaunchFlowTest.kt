package com.linkforty.sdk

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.linkforty.sdk.models.DeepLinkData
import com.linkforty.sdk.models.InstallResponse
import com.linkforty.sdk.models.LinkFortyConfig
import com.linkforty.sdk.storage.StorageKeys
import android.net.Uri
import com.linkforty.sdk.errors.LinkFortyError
import com.linkforty.sdk.models.CreateLinkOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * [LinkForty.initialize] across launches. Each launch is `reset()` followed by
 * `initialize()` over the same storage, as when the app is killed and reopened.
 */
@RunWith(AndroidJUnit4::class)
class LaunchFlowTest {

    private lateinit var server: MockWebServer
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        clearStoredData()
    }

    @After
    fun tearDown() {
        clearStoredData()
        server.shutdown()
    }

    /** Uninstall: drop the SDK instance and everything it stored. */
    private fun clearStoredData() {
        runCatching { LinkForty.shared.reset() }
        context.getSharedPreferences(StorageKeys.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun config(attributionWindowHours: Int = 168) = LinkFortyConfig(
        baseURL = server.url("/").toString().trimEnd('/'),
        apiKey = "test-key",
        attributionWindowHours = attributionWindowHours
    )

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private fun enqueueOrganicInstall() {
        server.enqueue(json("""{"installId":"install-1","attributed":false,"confidenceScore":0,"matchedFactors":[],"deepLinkData":null}"""))
    }

    /** Lets callbacks dispatched to the main thread run. */
    private suspend fun settle() {
        delay(500)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun enqueueAttributedInstall() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"installId":"install-1","attributed":true,"confidenceScore":90.0,""" +
                        """"matchedFactors":["ip"],"deepLinkData":{"shortCode":"welcome"}}"""
                )
        )
    }

    /** Initializes the SDK and returns the deferred deep links delivered on this launch. */
    private suspend fun launch(): Pair<InstallResponse, List<DeepLinkData?>> {
        runCatching { LinkForty.shared.reset() }
        val response = LinkForty.initialize(context, config())

        val deferred = mutableListOf<DeepLinkData?>()
        LinkForty.shared.onDeferredDeepLink { deferred.add(it) }

        // Callbacks are dispatched to the main thread; let them run.
        delay(500)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        return response to deferred.toList()
    }

    @Test
    fun firstLaunchReportsInstallAndDeliversDeferredDeepLink() = runBlocking {
        enqueueAttributedInstall()

        val (response, deferred) = launch()

        assertEquals("/api/sdk/v1/install", server.takeRequest(5, TimeUnit.SECONDS)?.path)
        assertEquals("install-1", response.installId)
        assertEquals(1, deferred.size)
        assertEquals("welcome", deferred.first()?.shortCode)
    }

    @Test
    fun laterLaunchReportsNoInstallAndDeliversNoDeferredDeepLink() = runBlocking {
        enqueueAttributedInstall()
        launch()
        server.takeRequest(5, TimeUnit.SECONDS)
        val requestsAfterFirstLaunch = server.requestCount

        val (response, deferred) = launch()

        assertEquals("a later launch must not report another install", requestsAfterFirstLaunch, server.requestCount)
        assertEquals("install-1", response.installId)
        assertTrue(response.attributed)
        assertEquals("welcome", response.deepLinkData?.shortCode)
        assertTrue("a later launch must not deliver the deferred deep link again", deferred.isEmpty())
        assertEquals("welcome", LinkForty.shared.getInstallData()?.shortCode)
    }

    @Test
    fun isFirstLaunchIsTrueBeforeAndAfterInitializeOnFirstLaunch() = runBlocking {
        assertTrue(LinkForty.isFirstLaunch(context))

        enqueueAttributedInstall()
        launch()

        assertTrue(LinkForty.shared.isFirstLaunch())
        assertTrue(LinkForty.isFirstLaunch(context))
    }

    @Test
    fun isFirstLaunchIsFalseBeforeInitializeOnLaterLaunch() = runBlocking {
        enqueueAttributedInstall()
        launch()
        LinkForty.shared.reset()

        assertFalse(LinkForty.isFirstLaunch(context))

        launch()
        assertFalse(LinkForty.shared.isFirstLaunch())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun organicFirstLaunchDeliversNullOnceAndLaterLaunchesNothing() = runBlocking {
        enqueueOrganicInstall()

        val (_, first) = launch()
        val (_, second) = launch()

        assertEquals(listOf<DeepLinkData?>(null), first)
        assertTrue(second.isEmpty())
    }

    @Test
    fun initializeThatFailsCanBeRetried() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("bad request"))

        val failed = runCatching { LinkForty.initialize(context, config()) }
        assertTrue(failed.exceptionOrNull() is LinkFortyError.InvalidResponse)
        assertTrue(runCatching { LinkForty.shared }.isFailure)

        enqueueAttributedInstall()
        val response = LinkForty.initialize(context, config())
        assertEquals("install-1", response.installId)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun configuredAttributionWindowIsSent() = runBlocking {
        enqueueAttributedInstall()

        LinkForty.initialize(context, config(attributionWindowHours = 24))

        val body = server.takeRequest(5, TimeUnit.SECONDS)?.body?.readUtf8().orEmpty()
        assertTrue(body, body.contains("\"attributionWindowHours\":24"))
    }

    @Test
    fun concurrentInitializeReportsOneInstall() = runBlocking {
        enqueueAttributedInstall()
        enqueueAttributedInstall()

        // On two threads at once, as from Application.onCreate and an Activity.
        val first = async(Dispatchers.Default) { runCatching { LinkForty.initialize(context, config()) } }
        val second = async(Dispatchers.Default) { runCatching { LinkForty.initialize(context, config()) } }
        val results = listOf(first.await(), second.await())

        assertEquals(1, results.count { it.isSuccess })
        assertTrue(results.single { it.isFailure }.exceptionOrNull() is LinkFortyError.AlreadyInitialized)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun clearDataKeepsDeepLinkCallbacks() = runBlocking {
        enqueueAttributedInstall()
        LinkForty.initialize(context, config())
        val opened = mutableListOf<String?>()
        LinkForty.shared.onDeepLink { _, data -> opened.add(data?.shortCode) }

        LinkForty.shared.clearData()
        server.enqueue(json("""{"shortCode":"abc","linkId":"link-1"}"""))
        LinkForty.shared.handleDeepLink(Uri.parse("https://go.example.com/abc"))
        settle()

        assertEquals(listOf<String?>("abc"), opened)
    }

    @Test
    fun createLinkWithTemplateUsesTheServerShortUrl() = runBlocking {
        enqueueAttributedInstall()
        LinkForty.initialize(context, config())
        server.enqueue(json("""{"id":"link-1","short_code":"abc","short_url":"https://links.example.com/promo/abc"}"""))

        val result = LinkForty.shared.createLink(CreateLinkOptions(templateId = "template-1", templateSlug = "promo"))

        assertEquals("https://links.example.com/promo/abc", result.url)
        assertNull(result.deduplicated)
    }
}
