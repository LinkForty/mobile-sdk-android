package com.linkforty.sdk.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Abstraction over HTTP calls for testability.
 */
internal interface HttpClient {
    /**
     * Executes an HTTP request and returns the raw response body bytes and status code.
     */
    suspend fun execute(
        url: String,
        method: HttpMethod,
        body: ByteArray? = null,
        headers: Map<String, String> = emptyMap()
    ): HttpResponse
}

/**
 * Raw HTTP response container.
 */
internal data class HttpResponse(
    val statusCode: Int,
    val body: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HttpResponse) return false
        return statusCode == other.statusCode && body.contentEquals(other.body)
    }

    override fun hashCode(): Int {
        var result = statusCode
        result = 31 * result + body.contentHashCode()
        return result
    }
}

/**
 * OkHttp-based [HttpClient] implementation.
 */
internal class OkHttpClientWrapper(
    timeoutSeconds: Long = 30
) : HttpClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun execute(
        url: String,
        method: HttpMethod,
        body: ByteArray?,
        headers: Map<String, String>
    ): HttpResponse = withContext(Dispatchers.IO) {
        val requestBody = body?.toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url(url)
            .method(method.value, requestBody)
            .apply {
                headers.forEach { (key, value) -> addHeader(key, value) }
            }
            .build()

        val response: Response = client.newCall(request).execute()
        val responseBody = response.body?.bytes() ?: ByteArray(0)

        HttpResponse(
            statusCode = response.code,
            body = responseBody
        )
    }
}
