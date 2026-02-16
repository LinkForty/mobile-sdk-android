package com.linkforty.sdk.errors

/**
 * Errors that can occur when using the LinkForty SDK.
 */
sealed class LinkFortyError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** The SDK has not been initialized. Call `LinkForty.initialize()` first. */
    class NotInitialized : LinkFortyError(
        "LinkForty SDK is not initialized. Call initialize() first."
    )

    /** The SDK has already been initialized. */
    class AlreadyInitialized : LinkFortyError(
        "LinkForty SDK has already been initialized."
    )

    /** The configuration is invalid. */
    class InvalidConfiguration(detail: String) : LinkFortyError(
        "Invalid configuration: $detail"
    )

    /** A network error occurred. */
    class NetworkError(cause: Throwable) : LinkFortyError(
        "Network error: ${cause.message}",
        cause
    )

    /** The server returned an invalid response. */
    class InvalidResponse(val statusCode: Int?, val responseMessage: String?) : LinkFortyError(
        buildString {
            append("Invalid server response")
            if (statusCode != null) append(" (status: $statusCode)")
            if (responseMessage != null) append(": $responseMessage")
        }
    )

    /** Failed to decode the response. */
    class DecodingError(cause: Throwable) : LinkFortyError(
        "Failed to decode response: ${cause.message}",
        cause
    )

    /** Failed to encode the request. */
    class EncodingError(cause: Throwable) : LinkFortyError(
        "Failed to encode request: ${cause.message}",
        cause
    )

    /** Invalid event data. */
    class InvalidEventData(detail: String) : LinkFortyError(
        "Invalid event data: $detail"
    )

    /** Invalid deep link URL. */
    class InvalidDeepLinkUrl(detail: String) : LinkFortyError(
        "Invalid deep link URL: $detail"
    )

    /** API key is required for this operation. */
    class MissingApiKey : LinkFortyError(
        "API key is required for this operation. Provide an apiKey in LinkFortyConfig."
    )
}
