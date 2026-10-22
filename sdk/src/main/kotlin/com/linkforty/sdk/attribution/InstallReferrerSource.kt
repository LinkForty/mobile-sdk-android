package com.linkforty.sdk.attribution

import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.linkforty.sdk.LinkFortyLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Where the install referrer comes from.
 */
internal fun interface InstallReferrerSource {
    /** The install referrer, or null when there is none. Never throws. */
    suspend fun read(): String?
}

/**
 * Reads the referrer Google Play recorded for this install (Play Install
 * Referrer API). A LinkForty link puts its click id in the referrer of every
 * Play listing it sends a visitor to, so the server can match the install to
 * that click exactly.
 *
 * Returns null without Play (another store, a sideload, an emulator without
 * Play services), when Play doesn't answer within [timeoutMs], and on any
 * error: the install is then matched by fingerprint, as without a referrer.
 * A service that is briefly unavailable is asked once more within the timeout.
 */
internal class PlayInstallReferrerSource(
    private val context: Context,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS
) : InstallReferrerSource {

    override suspend fun read(): String? {
        val referrer = try {
            withTimeoutOrNull(timeoutMs) {
                var result = query()
                // Play's service can be briefly unavailable, or drop the
                // connection, while the device is busy: try once more.
                if (result is Answer.TryAgain) {
                    delay(RETRY_DELAY_MS)
                    result = query()
                }
                (result as? Answer.Referrer)?.value
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LinkFortyLogger.log("Play install referrer unavailable: $e")
            null
        }
        return referrer?.takeIf { it.isNotBlank() }
    }

    private sealed interface Answer {
        data class Referrer(val value: String?) : Answer
        object TryAgain : Answer
        object None : Answer
    }

    private suspend fun query(): Answer = suspendCancellableCoroutine { continuation ->
        val client = InstallReferrerClient.newBuilder(context).build()
        continuation.invokeOnCancellation { runCatching { client.endConnection() } }
        client.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                val answer = when (responseCode) {
                    InstallReferrerClient.InstallReferrerResponse.OK ->
                        Answer.Referrer(runCatching { client.installReferrer.installReferrer }.getOrNull())
                    InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE,
                    InstallReferrerClient.InstallReferrerResponse.SERVICE_DISCONNECTED -> Answer.TryAgain
                    else -> Answer.None
                }
                if (answer !is Answer.Referrer) LinkFortyLogger.log("Play install referrer: response code $responseCode")
                runCatching { client.endConnection() }
                if (continuation.isActive) continuation.resume(answer)
            }

            override fun onInstallReferrerServiceDisconnected() {
                if (continuation.isActive) continuation.resume(Answer.TryAgain)
            }
        })
    }

    companion object {
        /** How long the install report waits for Play, retry included. */
        const val DEFAULT_TIMEOUT_MS = 2_000L

        /** The pause before asking Play again. */
        private const val RETRY_DELAY_MS = 250L
    }
}
