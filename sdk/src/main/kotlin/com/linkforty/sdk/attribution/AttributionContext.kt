package com.linkforty.sdk.attribution

import com.linkforty.sdk.LinkFortyLogger
import com.linkforty.sdk.storage.StorageManagerProtocol
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.time.Instant
import java.util.UUID

/**
 * The active last-click attribution: the deep link currently credited for in-app
 * activity, and when it opened the app.
 */
@JsonClass(generateAdapter = true)
data class ActiveAttribution(
    val linkId: String,
    val clickId: String? = null,
    /** ISO 8601 timestamp of when the deep link opened the app. */
    val openedAt: String
)

/**
 * The attribution fields merged into every event payload. [sessionId] is always
 * present; the link fields are absent until a deep link has opened the app.
 */
data class AttributionStamp(
    val attributedLinkId: String?,
    val attributedClickId: String?,
    val linkOpenedAt: String?,
    val sessionId: String
)

/**
 * Last-click attribution + session tracking for in-app events.
 *
 * LinkForty attributes in-app activity (events and screen views) to the deep link
 * that drove it, using a last-click + window model:
 *
 * - Every deep-link open (deferred install OR direct re-engagement) pins an active
 *   context to THAT link. The newest open wins (supersede).
 * - Every event is stamped with the active context so the backend can credit the
 *   link. The conversion window and session grouping are applied server-side at
 *   query time — the SDK only reports the active link, when it opened, and the
 *   current session.
 * - A [sessionId] identifies one app-open journey: generated on cold start and
 *   rotated on each new deep-link open.
 *
 * The active context is persisted so a reopen without a new click still attributes
 * to the last link. The session is in-memory: a cold start is a new session.
 */
internal class AttributionContext(
    private val storage: StorageManagerProtocol,
    private val debug: Boolean = false,
    moshi: Moshi = Moshi.Builder().build()
) {
    private val adapter = moshi.adapter(ActiveAttribution::class.java)
    private val lock = Any()

    private var active: ActiveAttribution? = loadActive()

    @Volatile
    private var sessionId: String = UUID.randomUUID().toString()

    /**
     * Records a deep-link open. The newest open supersedes the previous one
     * (last-click) and starts a new session. A no-op when [linkId] is null
     * (organic/unresolved open) — there is nothing to attribute to.
     */
    fun recordDeepLinkOpen(linkId: String?, clickId: String? = null) {
        if (linkId == null) return

        val attribution = ActiveAttribution(
            linkId = linkId,
            clickId = clickId,
            openedAt = Instant.now().toString()
        )

        val session: String
        synchronized(lock) {
            active = attribution
            // A new deep-link open is the start of a new attributed journey.
            sessionId = UUID.randomUUID().toString()
            session = sessionId
        }

        try {
            storage.saveAttribution(adapter.toJson(attribution))
        } catch (e: Exception) {
            if (debug) LinkFortyLogger.log("Failed to persist attribution: ${e.message}")
        }

        if (debug) LinkFortyLogger.log("Attribution context set: link=$linkId session=$session")
    }

    /** The attribution fields to merge into every event payload. */
    fun getStamp(): AttributionStamp = synchronized(lock) {
        AttributionStamp(
            attributedLinkId = active?.linkId,
            attributedClickId = active?.clickId,
            linkOpenedAt = active?.openedAt,
            sessionId = sessionId
        )
    }

    /** The current session id (one app-open journey). */
    fun currentSessionId(): String = sessionId

    /** Clears the persisted context and starts a fresh session (used by clearData). */
    fun clear() {
        synchronized(lock) {
            active = null
            sessionId = UUID.randomUUID().toString()
        }
        try {
            storage.removeAttribution()
        } catch (_: Exception) {
            // best-effort
        }
    }

    private fun loadActive(): ActiveAttribution? {
        return try {
            val raw = storage.getAttribution() ?: return null
            adapter.fromJson(raw)
        } catch (_: Exception) {
            // Missing/corrupt data (or an unstubbed mock in tests) → no active context.
            null
        }
    }
}
