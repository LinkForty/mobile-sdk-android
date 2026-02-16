package com.linkforty.sdk.linkcreation

import com.linkforty.sdk.models.CreateLinkOptions
import com.linkforty.sdk.models.CreateLinkResult
import com.linkforty.sdk.models.DashboardCreateLinkResponse
import com.linkforty.sdk.models.UTMParameters
import com.squareup.moshi.Moshi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CreateLinkTest {

    private val moshi = Moshi.Builder().build()

    @Test
    fun `CreateLinkOptions encodes to JSON correctly`() {
        val options = CreateLinkOptions(
            deepLinkParameters = mapOf("route" to "VIDEO_VIEWER", "id" to "vid123"),
            title = "Check this out!",
            utmParameters = UTMParameters(source = "app", campaign = "share")
        )

        val adapter = moshi.adapter(CreateLinkOptions::class.java)
        val json = adapter.toJson(options)

        assert(json.contains("\"deepLinkParameters\""))
        assert(json.contains("\"VIDEO_VIEWER\""))
        assert(json.contains("\"title\""))
        assert(json.contains("\"utmParameters\""))
    }

    @Test
    fun `CreateLinkOptions with templateId encodes correctly`() {
        val options = CreateLinkOptions(
            templateId = "tmpl-uuid-123",
            templateSlug = "promo",
            title = "Promo Link"
        )

        val adapter = moshi.adapter(CreateLinkOptions::class.java)
        val json = adapter.toJson(options)

        assert(json.contains("\"templateId\""))
        assert(json.contains("\"templateSlug\""))
        assert(json.contains("\"tmpl-uuid-123\""))
    }

    @Test
    fun `CreateLinkOptions with no fields encodes to empty-like JSON`() {
        val options = CreateLinkOptions()

        val adapter = moshi.adapter(CreateLinkOptions::class.java)
        val json = adapter.toJson(options)

        assertNull(options.templateId)
        assertNull(options.title)
        assertNull(options.customCode)
        assertNotNull(json)
    }

    @Test
    fun `CreateLinkResult decodes from JSON correctly`() {
        val json = """
            {
                "url": "https://go.example.com/tmpl/abc123",
                "shortCode": "abc123",
                "linkId": "link-uuid-456"
            }
        """.trimIndent()

        val adapter = moshi.adapter(CreateLinkResult::class.java)
        val result = adapter.fromJson(json)

        assertNotNull(result)
        assertEquals("https://go.example.com/tmpl/abc123", result!!.url)
        assertEquals("abc123", result.shortCode)
        assertEquals("link-uuid-456", result.linkId)
    }

    @Test
    fun `DashboardCreateLinkResponse decodes snake_case fields`() {
        val json = """
            {
                "id": "link-uuid-789",
                "short_code": "xyz789"
            }
        """.trimIndent()

        val adapter = moshi.adapter(DashboardCreateLinkResponse::class.java)
        val response = adapter.fromJson(json)

        assertNotNull(response)
        assertEquals("link-uuid-789", response!!.id)
        assertEquals("xyz789", response.shortCode)
    }
}
