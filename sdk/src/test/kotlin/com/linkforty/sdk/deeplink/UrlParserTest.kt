package com.linkforty.sdk.deeplink

import android.net.Uri
import com.linkforty.sdk.utilities.UrlParser
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UrlParserTest {

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

    // -- extractShortCode Tests --

    @Test
    fun `extractShortCode returns last path segment`() {
        val uri = mockUri(pathSegments = listOf("abc123"))
        assertEquals("abc123", UrlParser.extractShortCode(uri))
    }

    @Test
    fun `extractShortCode returns last segment for template URLs`() {
        val uri = mockUri(pathSegments = listOf("promo", "xyz789"))
        assertEquals("xyz789", UrlParser.extractShortCode(uri))
    }

    @Test
    fun `extractShortCode returns null for empty path`() {
        val uri = mockUri(pathSegments = emptyList())
        assertNull(UrlParser.extractShortCode(uri))
    }

    // -- extractUTMParameters Tests --

    @Test
    fun `extractUTMParameters returns all UTM params`() {
        val uri = mockUri(
            pathSegments = listOf("abc"),
            queryParams = mapOf(
                "utm_source" to "google",
                "utm_medium" to "cpc",
                "utm_campaign" to "spring",
                "utm_term" to "shoes",
                "utm_content" to "banner"
            )
        )

        val utm = UrlParser.extractUTMParameters(uri)
        assertNotNull(utm)
        assertEquals("google", utm!!.source)
        assertEquals("cpc", utm.medium)
        assertEquals("spring", utm.campaign)
        assertEquals("shoes", utm.term)
        assertEquals("banner", utm.content)
    }

    @Test
    fun `extractUTMParameters returns partial UTM params`() {
        val uri = mockUri(
            pathSegments = listOf("abc"),
            queryParams = mapOf("utm_source" to "facebook")
        )

        val utm = UrlParser.extractUTMParameters(uri)
        assertNotNull(utm)
        assertEquals("facebook", utm!!.source)
        assertNull(utm.medium)
    }

    @Test
    fun `extractUTMParameters returns null when no UTM params`() {
        val uri = mockUri(
            pathSegments = listOf("abc"),
            queryParams = mapOf("productId" to "123")
        )

        assertNull(UrlParser.extractUTMParameters(uri))
    }

    // -- extractCustomParameters Tests --

    @Test
    fun `extractCustomParameters returns non-UTM params`() {
        val uri = mockUri(
            pathSegments = listOf("abc"),
            queryParams = mapOf(
                "utm_source" to "google",
                "productId" to "123",
                "color" to "blue"
            )
        )

        val custom = UrlParser.extractCustomParameters(uri)
        assertEquals(2, custom.size)
        assertEquals("123", custom["productId"])
        assertEquals("blue", custom["color"])
    }

    @Test
    fun `extractCustomParameters returns empty map when only UTM params`() {
        val uri = mockUri(
            pathSegments = listOf("abc"),
            queryParams = mapOf("utm_source" to "google", "utm_medium" to "cpc")
        )

        assertTrue(UrlParser.extractCustomParameters(uri).isEmpty())
    }

    @Test
    fun `extractCustomParameters returns empty map when no query params`() {
        val uri = mockUri(pathSegments = listOf("abc"))
        assertTrue(UrlParser.extractCustomParameters(uri).isEmpty())
    }

    // -- parseDeepLink Tests --

    @Test
    fun `parseDeepLink creates DeepLinkData from URL`() {
        val uri = mockUri(
            pathSegments = listOf("abc123"),
            queryParams = mapOf(
                "utm_source" to "facebook",
                "productId" to "456"
            )
        )

        val data = UrlParser.parseDeepLink(uri)
        assertNotNull(data)
        assertEquals("abc123", data!!.shortCode)
        assertNotNull(data.androidURL)
        assertEquals("facebook", data.utmParameters?.source)
        assertEquals("456", data.customParameters?.get("productId"))
    }

    @Test
    fun `parseDeepLink returns null for empty path`() {
        val uri = mockUri(pathSegments = emptyList())
        assertNull(UrlParser.parseDeepLink(uri))
    }

    @Test
    fun `parseDeepLink sets androidURL to full URL string`() {
        val uri = mockUri(pathSegments = listOf("test"))
        val data = UrlParser.parseDeepLink(uri)

        assertNotNull(data?.androidURL)
        assertTrue(data!!.androidURL!!.contains("go.example.com"))
    }

    @Test
    fun `parseDeepLink sets customParameters to null when empty`() {
        val uri = mockUri(pathSegments = listOf("abc"))
        val data = UrlParser.parseDeepLink(uri)

        assertNull(data?.customParameters)
    }
}
