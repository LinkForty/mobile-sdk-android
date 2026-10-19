package com.linkforty.sdk

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.linkforty.sdk.models.LinkFortyConfig
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Runs the real initialize/install flow on a device under each locale, against
 * an in-process server, and checks the language the install request reports.
 *
 * Covers the shapes `Locale.toLanguageTag()` takes on real devices: Unicode
 * extensions from regional preferences (first day of week, temperature unit,
 * calendar, numbering system), script subtags, and UN M.49 regions.
 */
@RunWith(AndroidJUnit4::class)
class LocaleInstallTest {

    private lateinit var server: MockWebServer
    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
        server.shutdown()
    }

    @Test
    fun installReportsLanguageRegionForEveryLocale() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val baseUrl = server.url("/").toString().trimEnd('/')
        val pattern = Regex("^[a-z]{2,3}(-([A-Z]{2}|[0-9]{3}))?$")

        for ((tag, expected) in CASES) {
            Locale.setDefault(Locale.forLanguageTag(tag))
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"installId":"install-$tag","attributed":false,"confidenceScore":0.0,"matchedFactors":[]}""")
            )

            val response = LinkForty.initialize(context, LinkFortyConfig(baseURL = baseUrl))
            assertEquals("initialize under $tag", "install-$tag", response.installId)

            val request = server.takeRequest(10, TimeUnit.SECONDS)
                ?: throw AssertionError("no install request under $tag")
            assertEquals("/api/sdk/v1/install", request.path)
            val language = JSONObject(request.body.readUtf8()).getString("language")

            assertTrue("$tag sent '$language' (API ${Build.VERSION.SDK_INT})", pattern.matches(language))
            assertTrue("$tag sent '$language', longer than 10", language.length <= 10)
            assertEquals("language sent under $tag (API ${Build.VERSION.SDK_INT})", expected, language)

            LinkForty.shared.clearData()
            LinkForty.shared.reset()
        }
    }

    private companion object {
        val CASES = listOf(
            "en-ES-u-fw-mon-mu-celsius" to "en-ES",
            "de-DE-u-ca-gregory-fw-mon-mu-celsius-nu-latn" to "de-DE",
            "ar-SA-u-nu-arab" to "ar-SA",
            "th-TH-u-ca-buddhist-nu-thai" to "th-TH",
            "zh-Hans-CN" to "zh-CN",
            "sr-Latn-RS" to "sr-RS",
            "he-IL" to "he-IL",
            "ja-JP-u-ca-japanese" to "ja-JP",
            "es-419" to "es-419",
            "fil-PH" to "fil-PH",
        )
    }
}
