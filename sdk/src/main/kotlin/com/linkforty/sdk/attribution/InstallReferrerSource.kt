package com.linkforty.sdk.attribution

import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.linkforty.sdk.LinkFortyLogger
import kotlinx.coroutines.CancellationException
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
 */
internal class PlayInstallReferrerSource(
    private val context: Context,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS
) : InstallReferrerSource {

    override suspend fun read(): String? {
        val referrer = try {
            withTimeoutOrNull(timeoutMs) { query() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LinkFortyLogger.log("Play install referrer unavailable: $e")
            null
        }
        return referrer?.takeIf { it.isNotBlank() }
    }

    private suspend fun query(): String? = suspendCancellableCoroutine { continuation ->
        val client = InstallReferrerClient.newBuilder(context).build()
        continuation.invokeOnCancellation { runCatching { client.endConnection() } }
        client.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                val referrer = if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                    runCatching { client.installReferrer.installReferrer }.getOrNull()
                } else {
                    LinkFortyLogger.log("Play install referrer: response code $responseCode")
                    null
                }
                runCatching { client.endConnection() }
                if (continuation.isActive) continuation.resume(referrer)
            }

            override fun onInstallReferrerServiceDisconnected() {
                if (continuation.isActive) continuation.resume(null)
            }
        })
    }

    companion object {
        /** How long the install report waits for Play. */
        const val DEFAULT_TIMEOUT_MS = 2_000L
    }
}
