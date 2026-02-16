package com.linkforty.sdk.models

import com.squareup.moshi.Moshi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DeepLinkDataTest {

    private val moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter(DeepLinkData::class.java)

    @Test
    fun `create deep link data with required fields only`() {
        val data = DeepLinkData(shortCode = "abc123")

        assertEquals("abc123", data.shortCode)
        assertNull(data.iosURL)
        assertNull(data.androidURL)
        assertNull(data.webURL)
        assertNull(data.utmParameters)
        assertNull(data.customParameters)
        assertNull(data.deepLinkPath)
        assertNull(data.appScheme)
        assertNull(data.clickedAt)
        assertNull(data.linkId)
    }

    @Test
    fun `create deep link data with all fields`() {
        val data = DeepLinkData(
            shortCode = "test123",
            iosURL = "https://example.com/ios",
            androidURL = "https://example.com/android",
            webURL = "https://example.com/web",
            utmParameters = UTMParameters(source = "google", campaign = "spring"),
            customParameters = mapOf("productId" to "789"),
            deepLinkPath = "/product/123",
            appScheme = "myapp",
            clickedAt = "2025-01-15T10:30:00Z",
            linkId = "link-uuid-123"
        )

        assertEquals("test123", data.shortCode)
        assertEquals("https://example.com/ios", data.iosURL)
        assertEquals("https://example.com/android", data.androidURL)
        assertEquals("https://example.com/web", data.webURL)
        assertEquals("google", data.utmParameters?.source)
        assertEquals("789", data.customParameters?.get("productId"))
        assertEquals("/product/123", data.deepLinkPath)
        assertEquals("myapp", data.appScheme)
        assertEquals("2025-01-15T10:30:00Z", data.clickedAt)
        assertEquals("link-uuid-123", data.linkId)
    }

    @Test
    fun `clickedAtDate parses ISO 8601 string`() {
        val data = DeepLinkData(
            shortCode = "abc",
            clickedAt = "2025-01-15T10:30:00Z"
        )

        val instant = data.clickedAtDate()
        assertNotNull(instant)
    }

    @Test
    fun `clickedAtDate returns null for invalid string`() {
        val data = DeepLinkData(
            shortCode = "abc",
            clickedAt = "not-a-date"
        )

        assertNull(data.clickedAtDate())
    }

    @Test
    fun `clickedAtDate returns null when clickedAt is null`() {
        val data = DeepLinkData(shortCode = "abc")
        assertNull(data.clickedAtDate())
    }

    @Test
    fun `JSON encoding uses correct field names`() {
        val data = DeepLinkData(
            shortCode = "abc123",
            iosURL = "https://ios.example.com",
            androidURL = "https://android.example.com",
            webURL = "https://web.example.com"
        )

        val json = adapter.toJson(data)
        assert(json.contains("\"iosUrl\""))
        assert(json.contains("\"androidUrl\""))
        assert(json.contains("\"webUrl\""))
        assert(json.contains("\"shortCode\""))
    }

    @Test
    fun `JSON decoding maps field names correctly`() {
        val json = """
            {
                "shortCode": "abc123",
                "iosUrl": "https://ios.example.com",
                "androidUrl": "https://android.example.com",
                "webUrl": "https://web.example.com",
                "deepLinkPath": "/product/123",
                "appScheme": "myapp",
                "linkId": "uuid-123"
            }
        """.trimIndent()

        val data = adapter.fromJson(json)
        assertNotNull(data)
        assertEquals("abc123", data!!.shortCode)
        assertEquals("https://ios.example.com", data.iosURL)
        assertEquals("https://android.example.com", data.androidURL)
        assertEquals("https://web.example.com", data.webURL)
        assertEquals("/product/123", data.deepLinkPath)
        assertEquals("myapp", data.appScheme)
        assertEquals("uuid-123", data.linkId)
    }

    @Test
    fun `JSON round-trip preserves data`() {
        val original = DeepLinkData(
            shortCode = "round",
            iosURL = "https://ios.example.com",
            androidURL = "https://android.example.com",
            utmParameters = UTMParameters(source = "test", medium = "email"),
            customParameters = mapOf("key" to "value"),
            deepLinkPath = "/test",
            linkId = "id-1"
        )

        val json = adapter.toJson(original)
        val decoded = adapter.fromJson(json)

        assertEquals(original, decoded)
    }

    @Test
    fun `data class equality works`() {
        val data1 = DeepLinkData(shortCode = "abc", linkId = "123")
        val data2 = DeepLinkData(shortCode = "abc", linkId = "123")
        val data3 = DeepLinkData(shortCode = "xyz", linkId = "456")

        assertEquals(data1, data2)
        assert(data1 != data3)
    }
}
