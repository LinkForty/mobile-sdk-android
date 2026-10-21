package com.linkforty.sdk

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.linkforty.sdk.models.DeepLinkData
import com.linkforty.sdk.models.InstallResponse
import com.linkforty.sdk.models.LinkFortyConfig
import com.linkforty.sdk.storage.StorageKeys
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun config() = LinkFortyConfig(baseURL = server.url("/").toString().trimEnd('/'))

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
}
