package com.linkforty.sdk.network

import com.linkforty.sdk.LinkFortyLogger
import com.linkforty.sdk.errors.LinkFortyError
import com.linkforty.sdk.models.AnyJsonAdapter
import com.linkforty.sdk.models.LinkFortyConfig
import com.squareup.moshi.Moshi
import kotlinx.coroutines.delay
import kotlin.math.pow

/**
 * Protocol for dependency injection in tests.
 */
internal interface NetworkManagerProtocol {
    suspend fun <T> request(
        endpoint: String,
        method: HttpMethod,
        body: Any? = null,
        headers: Map<String, String>? = null,
        responseType: Class<T>
    ): T
}

/**
 * Inline reified convenience for [NetworkManagerProtocol.request].
 */
internal suspend inline fun <reified T> NetworkManagerProtocol.request(
    endpoint: String,
    method: HttpMethod,
    body: Any? = null,
    headers: Map<String, String>? = null
): T = request(endpoint, method, body, headers, T::class.java)

/**
 * Manages network requests for the SDK.
 */
internal class NetworkManager(
    private val config: LinkFortyConfig,
    private val httpClient: HttpClient = OkHttpClientWrapper()
) : NetworkManagerProtocol {

    private val moshi: Moshi = Moshi.Builder()
        .add(AnyJsonAdapter())
        .build()

    /** Maximum number of retry attempts */
    private val maxRetries = 3

    /**
     * Performs a network request with automatic retry and decodes the response.
     *
     * @param endpoint API endpoint path (e.g., "/api/sdk/v1/install")
     * @param method HTTP method
     * @param body Optional request body (will be serialized to JSON)
     * @param headers Optional additional headers
     * @param responseType Class of the expected response type
     * @return Decoded response of type T
     * @throws LinkFortyError on failure
     */
    override suspend fun <T> request(
        endpoint: String,
        method: HttpMethod,
        body: Any?,
        headers: Map<String, String>?,
        responseType: Class<T>
    ): T {
        var lastError: Exception? = null

        for (attempt in 1..maxRetries) {
            try {
                return performRequest(endpoint, method, body, headers, responseType)
            } catch (e: LinkFortyError) {
                lastError = e

                // Don't retry on client errors (4xx) or non-retryable errors
                when (e) {
                    is LinkFortyError.InvalidResponse -> {
                        val code = e.statusCode
                        if (code != null && code in 400..<500) throw e
                    }
                    is LinkFortyError.InvalidConfiguration,
                    is LinkFortyError.DecodingError,
                    is LinkFortyError.EncodingError -> throw e
                    else -> { /* retryable */ }
                }

                // Exponential backoff: 1s, 2s, 4s
                if (attempt < maxRetries) {
                    val delayMs = 2.0.pow((attempt - 1).toDouble()).toLong() * 1000
                    LinkFortyLogger.log(
                        "Request failed (attempt $attempt/$maxRetries), retrying in ${delayMs / 1000}s..."
                    )
                    delay(delayMs)
                }
            } catch (e: Exception) {
                throw LinkFortyError.NetworkError(e)
            }
        }

        throw lastError ?: LinkFortyError.NetworkError(RuntimeException("Request failed after $maxRetries attempts"))
    }

    /**
     * Convenience inline reified wrapper for cleaner call sites.
     */
    suspend inline fun <reified T> request(
        endpoint: String,
        method: HttpMethod,
        body: Any? = null,
        headers: Map<String, String>? = null
    ): T = request(endpoint, method, body, headers, T::class.java)

    // -- Private --

    private suspend fun <T> performRequest(
        endpoint: String,
        method: HttpMethod,
        body: Any?,
        headers: Map<String, String>?,
        responseType: Class<T>
    ): T {
        // Build URL
        val baseUrl = config.baseURL.trimEnd('/')
        val url = "$baseUrl$endpoint"

        // Build headers
        val requestHeaders = mutableMapOf<String, String>()
        requestHeaders["Content-Type"] = "application/json"

        config.apiKey?.let { apiKey ->
            requestHeaders["Authorization"] = "Bearer $apiKey"
        }

        headers?.forEach { (key, value) ->
            requestHeaders[key] = value
        }

        // Encode body
        val bodyBytes: ByteArray? = if (body != null) {
            try {
                @Suppress("UNCHECKED_CAST")
                val adapter = moshi.adapter(body::class.java as Class<Any>)
                adapter.toJson(body).toByteArray(Charsets.UTF_8)
            } catch (e: Exception) {
                throw LinkFortyError.EncodingError(e)
            }
        } else null

        // Log request in debug mode
        if (config.debug) {
            logRequest(method, url, bodyBytes)
        }

        // Perform request
        val response = httpClient.execute(url, method, bodyBytes, requestHeaders)

        // Log response in debug mode
        if (config.debug) {
            logResponse(response)
        }

        // Check status code
        if (response.statusCode !in 200..<300) {
            val message = response.body.toString(Charsets.UTF_8)
            throw LinkFortyError.InvalidResponse(response.statusCode, message)
        }

        // Decode response
        return try {
            val adapter = moshi.adapter(responseType)
            adapter.fromJson(response.body.toString(Charsets.UTF_8))
                ?: throw LinkFortyError.DecodingError(NullPointerException("Decoded response was null"))
        } catch (e: LinkFortyError) {
            throw e
        } catch (e: Exception) {
            throw LinkFortyError.DecodingError(e)
        }
    }

    private fun logRequest(method: HttpMethod, url: String, body: ByteArray?) {
        val log = buildString {
            append("[LinkForty] -> ${method.value} $url")
            config.apiKey?.let { apiKey ->
                append("\n  Authorization: Bearer ***${apiKey.takeLast(4)}")
            }
            body?.let { bytes ->
                append("\n  Body: ${bytes.toString(Charsets.UTF_8)}")
            }
        }
        LinkFortyLogger.log(log)
    }

    private fun logResponse(response: HttpResponse) {
        val log = buildString {
            append("[LinkForty] <- ${response.statusCode}")
            append("\n  Response: ${response.body.toString(Charsets.UTF_8)}")
        }
        LinkFortyLogger.log(log)
    }
}
