package com.linkforty.sdk.network

import com.linkforty.sdk.errors.LinkFortyError
import com.linkforty.sdk.models.InstallResponse
import com.linkforty.sdk.models.LinkFortyConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NetworkManagerTest {

    private lateinit var config: LinkFortyConfig
    private lateinit var mockHttpClient: MockHttpClient
    private lateinit var sut: NetworkManager
    private val moshi = Moshi.Builder().build()

    @BeforeEach
    fun setUp() {
        config = LinkFortyConfig(
            baseURL = "https://api.example.com",
            apiKey = "test-api-key",
            debug = false
        )
        mockHttpClient = MockHttpClient()
        sut = NetworkManager(config, mockHttpClient)
    }

    // -- Success Tests --

    @Test
    fun `successful GET request decodes response`() = runTest {
        val responseJson = """
            {
                "installId": "test-id",
                "attributed": true,
                "confidenceScore": 85.0,
                "matchedFactors": ["userAgent", "timezone"],
                "deepLinkData": {"shortCode": "abc123"}
            }
        """.trimIndent()

        mockHttpClient.mockResponse = HttpResponse(200, responseJson.toByteArray())

        val result: InstallResponse = sut.request(
            endpoint = "/test",
            method = HttpMethod.GET
        )

        assertEquals("test-id", result.installId)
        assertTrue(result.attributed)
        assertEquals(85.0, result.confidenceScore)
    }

    @Test
    fun `successful POST request with body`() = runTest {
        mockHttpClient.mockResponse = HttpResponse(201, """{"ok": true}""".toByteArray())

        val result: TestOkResponse = sut.request(
            endpoint = "/test",
            method = HttpMethod.POST,
            body = TestRequest("test", 42)
        )

        assertTrue(result.ok)
        assertNotNull(mockHttpClient.lastBody)
    }

    // -- Authentication Tests --

    @Test
    fun `request includes Authorization header`() = runTest {
        mockHttpClient.mockResponse = HttpResponse(200, """{"ok": true}""".toByteArray())

        sut.request<TestOkResponse>(
            endpoint = "/test",
            method = HttpMethod.GET
        )

        val authHeader = mockHttpClient.lastHeaders?.get("Authorization")
        assertEquals("Bearer test-api-key", authHeader)
    }

    @Test
    fun `request without API key has no auth header`() = runTest {
        val configNoKey = LinkFortyConfig(
            baseURL = "https://api.example.com",
            apiKey = null
        )
        val sutNoKey = NetworkManager(configNoKey, mockHttpClient)
        mockHttpClient.mockResponse = HttpResponse(200, """{"ok": true}""".toByteArray())

        sutNoKey.request<TestOkResponse>(
            endpoint = "/test",
            method = HttpMethod.GET
        )

        val authHeader = mockHttpClient.lastHeaders?.get("Authorization")
        assertEquals(null, authHeader)
    }

    // -- Error Tests --

    @Test
    fun `400 client error throws without retry`() = runTest {
        mockHttpClient.mockResponse = HttpResponse(400, "Bad Request".toByteArray())

        val error = assertThrows<LinkFortyError.InvalidResponse> {
            sut.request<TestOkResponse>(
                endpoint = "/test",
                method = HttpMethod.GET
            )
        }

        assertEquals(400, error.statusCode)
        // Should not retry on 4xx — only 1 request
        assertEquals(1, mockHttpClient.requestCount)
    }

    @Test
    fun `401 unauthorized error throws without retry`() = runTest {
        mockHttpClient.mockResponse = HttpResponse(401, "Unauthorized".toByteArray())

        val error = assertThrows<LinkFortyError.InvalidResponse> {
            sut.request<TestOkResponse>(
                endpoint = "/test",
                method = HttpMethod.GET
            )
        }

        assertEquals(401, error.statusCode)
        assertEquals(1, mockHttpClient.requestCount)
    }

    @Test
    fun `500 server error is retried`() = runTest {
        mockHttpClient.mockResponse = HttpResponse(500, "Server Error".toByteArray())

        assertThrows<LinkFortyError.InvalidResponse> {
            sut.request<TestOkResponse>(
                endpoint = "/test",
                method = HttpMethod.GET
            )
        }

        // Should retry maxRetries times
        assertEquals(3, mockHttpClient.requestCount)
    }

    @Test
    fun `network error wraps in LinkFortyError`() = runTest {
        mockHttpClient.mockError = RuntimeException("Connection refused")

        assertThrows<LinkFortyError.NetworkError> {
            sut.request<TestOkResponse>(
                endpoint = "/test",
                method = HttpMethod.GET
            )
        }
    }

    @Test
    fun `invalid JSON response throws decoding error`() = runTest {
        mockHttpClient.mockResponse = HttpResponse(200, "not json".toByteArray())

        assertThrows<LinkFortyError.DecodingError> {
            sut.request<TestOkResponse>(
                endpoint = "/test",
                method = HttpMethod.GET
            )
        }
    }

    // -- Headers Tests --

    @Test
    fun `Content-Type header is set`() = runTest {
        mockHttpClient.mockResponse = HttpResponse(200, """{"ok": true}""".toByteArray())

        sut.request<TestOkResponse>(
            endpoint = "/test",
            method = HttpMethod.POST,
            body = TestRequest("test", 1)
        )

        val contentType = mockHttpClient.lastHeaders?.get("Content-Type")
        assertEquals("application/json", contentType)
    }

    @Test
    fun `custom headers are included`() = runTest {
        mockHttpClient.mockResponse = HttpResponse(200, """{"ok": true}""".toByteArray())

        sut.request<TestOkResponse>(
            endpoint = "/test",
            method = HttpMethod.GET,
            headers = mapOf("X-Custom-Header" to "custom-value")
        )

        val customHeader = mockHttpClient.lastHeaders?.get("X-Custom-Header")
        assertEquals("custom-value", customHeader)
    }
}

// -- Test Helpers --

@JsonClass(generateAdapter = true)
data class TestRequest(val name: String, val value: Int)

@JsonClass(generateAdapter = true)
data class TestOkResponse(val ok: Boolean)

internal class MockHttpClient : HttpClient {
    var mockResponse: HttpResponse? = null
    var mockError: Exception? = null
    var lastUrl: String? = null
    var lastMethod: HttpMethod? = null
    var lastBody: ByteArray? = null
    var lastHeaders: Map<String, String>? = null
    var requestCount: Int = 0

    override suspend fun execute(
        url: String,
        method: HttpMethod,
        body: ByteArray?,
        headers: Map<String, String>
    ): HttpResponse {
        lastUrl = url
        lastMethod = method
        lastBody = body
        lastHeaders = headers
        requestCount++

        mockError?.let { throw it }

        return mockResponse
            ?: throw RuntimeException("No mock response configured")
    }
}
