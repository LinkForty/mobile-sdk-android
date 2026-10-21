package com.linkforty.sdk.models

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Decoding tests for [InstallResponse] using the same Moshi configuration the
 * SDK's network layer builds.
 */
class InstallResponseTest {

    private val moshi = Moshi.Builder()
        .add(LenientDeepLinkDataAdapter())
        .build()
    private val adapter = moshi.adapter(InstallResponse::class.java)

    // -- Organic Installs --

    /** The backend returns `deepLinkData: {}` (not null) for organic installs. */
    @Test
    fun `decodes empty deepLinkData object as null`() {
        val json = """
            {
                "installId": "install-123",
                "attributed": false,
                "confidenceScore": 0,
                "matchedFactors": [],
                "deepLinkData": {}
            }
        """.trimIndent()

        val response = adapter.fromJson(json)

        assertNotNull(response)
        assertEquals("install-123", response?.installId)
        assertFalse(response!!.attributed)
        assertEquals(0.0, response.confidenceScore)
        assertTrue(response.matchedFactors.isEmpty())
        assertNull(response.deepLinkData)
    }

    @Test
    fun `decodes null deepLinkData as null`() {
        val json = """
            {
                "installId": "install-123",
                "attributed": false,
                "confidenceScore": 0,
                "matchedFactors": [],
                "deepLinkData": null
            }
        """.trimIndent()

        assertNull(adapter.fromJson(json)?.deepLinkData)
    }

    @Test
    fun `decodes missing deepLinkData as null`() {
        val json = """
            {
                "installId": "install-123",
                "attributed": false,
                "confidenceScore": 0,
                "matchedFactors": []
            }
        """.trimIndent()

        assertNull(adapter.fromJson(json)?.deepLinkData)
    }

    /**
     * A deep link with no short code can't be routed to, so it is not worth
     * failing the whole response over.
     */
    @Test
    fun `decodes deepLinkData without a shortCode as null`() {
        val json = """
            {
                "installId": "install-123",
                "attributed": false,
                "confidenceScore": 0,
                "matchedFactors": [],
                "deepLinkData": {"iosUrl": "myapp://product/456"}
            }
        """.trimIndent()

        assertNull(adapter.fromJson(json)?.deepLinkData)
    }

    // -- Attributed Installs --

    @Test
    fun `decodes attributed response with deep link data`() {
        val json = """
            {
                "installId": "install-123",
                "attributed": true,
                "confidenceScore": 85,
                "matchedFactors": ["userAgent", "timezone"],
                "deepLinkData": {
                    "shortCode": "abc123",
                    "iosUrl": "myapp://product/456",
                    "deepLinkPath": "/product/456",
                    "clickedAt": "2026-01-15T10:30:00Z"
                }
            }
        """.trimIndent()

        val response = adapter.fromJson(json)

        assertNotNull(response)
        assertTrue(response!!.attributed)
        assertEquals(85.0, response.confidenceScore)
        assertEquals(listOf("userAgent", "timezone"), response.matchedFactors)
        assertEquals("abc123", response.deepLinkData?.shortCode)
        assertEquals("myapp://product/456", response.deepLinkData?.iosURL)
        assertEquals("/product/456", response.deepLinkData?.deepLinkPath)
        assertNotNull(response.deepLinkData?.clickedAtDate())
    }

    // -- Match Certainty --

    @Test
    fun `decodes attributionMethod and competingLinks on a match`() {
        val json = """
            {
                "installId": "install-123",
                "attributed": true,
                "confidenceScore": 85,
                "matchedFactors": ["userAgent", "timezone"],
                "attributionMethod": "fingerprint",
                "competingLinks": 2,
                "deepLinkData": {
                    "shortCode": "abc123",
                    "confidenceScore": 85,
                    "matchedFactors": ["userAgent", "timezone"],
                    "attributionMethod": "fingerprint",
                    "competingLinks": 2
                }
            }
        """.trimIndent()

        val response = adapter.fromJson(json)!!

        assertEquals("fingerprint", response.attributionMethod)
        assertEquals(2, response.competingLinks)
        val deepLink = response.deepLinkData!!
        assertEquals(85.0, deepLink.confidenceScore)
        assertEquals(listOf("userAgent", "timezone"), deepLink.matchedFactors)
        assertEquals("fingerprint", deepLink.attributionMethod)
        assertEquals(2, deepLink.competingLinks)
    }

    @Test
    fun `decodes an organic response with attributionMethod none`() {
        val json = """
            {
                "installId": "install-123",
                "attributed": false,
                "confidenceScore": 0,
                "matchedFactors": [],
                "attributionMethod": "none",
                "competingLinks": 0,
                "deepLinkData": {}
            }
        """.trimIndent()

        val response = adapter.fromJson(json)!!

        assertEquals("none", response.attributionMethod)
        assertEquals(0, response.competingLinks)
        assertNull(response.deepLinkData)
    }

    @Test
    fun `decodes a response without attributionMethod or competingLinks as null`() {
        val json = """
            {
                "installId": "install-123",
                "attributed": true,
                "confidenceScore": 85,
                "matchedFactors": ["userAgent"],
                "deepLinkData": {"shortCode": "abc123"}
            }
        """.trimIndent()

        val response = adapter.fromJson(json)!!

        assertNull(response.attributionMethod)
        assertNull(response.competingLinks)
        val deepLink = response.deepLinkData!!
        assertNull(deepLink.confidenceScore)
        assertNull(deepLink.matchedFactors)
        assertNull(deepLink.attributionMethod)
        assertNull(deepLink.competingLinks)
    }

    // -- Required Fields --

    @Test
    fun `throws when installId is missing`() {
        val json = """
            {
                "attributed": false,
                "confidenceScore": 0,
                "matchedFactors": []
            }
        """.trimIndent()

        assertThrows<JsonDataException> { adapter.fromJson(json) }
    }

    // -- Round Trip --

    @Test
    fun `round trips through encoding`() {
        val original = InstallResponse(
            installId = "install-123",
            attributed = true,
            confidenceScore = 85.0,
            matchedFactors = listOf("userAgent"),
            deepLinkData = DeepLinkData(shortCode = "abc123", iosURL = "myapp://product/456")
        )

        val decoded = adapter.fromJson(adapter.toJson(original))

        assertEquals(original, decoded)
    }

    @Test
    fun `round trips an organic response through encoding`() {
        val original = InstallResponse(
            installId = "install-123",
            attributed = false,
            confidenceScore = 0.0,
            matchedFactors = emptyList(),
            deepLinkData = null
        )

        val decoded = adapter.fromJson(adapter.toJson(original))

        assertEquals(original, decoded)
    }
}
