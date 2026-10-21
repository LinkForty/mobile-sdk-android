package com.linkforty.sdk.deeplink

import android.net.Uri
import com.linkforty.sdk.LinkFortyLogger
import com.linkforty.sdk.errors.LinkFortyError
import com.linkforty.sdk.models.LinkDomainsResponse
import com.linkforty.sdk.network.HttpMethod
import com.linkforty.sdk.network.NetworkManagerProtocol
import com.linkforty.sdk.network.request
import com.linkforty.sdk.storage.StorageManagerProtocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URI

/**
 * The hosts the workspace's links are served from, which decide whether a URL
 * handed to the SDK is looked up as a LinkForty link.
 *
 * The list comes from the server (it needs the app token), is stored on the
 * device and used from the next launch until it is fetched again. Until a list
 * is known, every web URL is looked up, as before the list existed.
 */
internal class LinkDomains(
    private val storage: StorageManagerProtocol,
    baseURL: String
) {

    private val baseHost: String? = hostOf(baseURL)

    /** The known hosts, or null while unknown. */
    @Volatile
    var known: Set<String>? = storage.getLinkDomains()?.let(::parse)
        private set

    private val fetched = CompletableDeferred<Unit>()

    /** Whether [uri] is looked up on the server; see [isLinkFortyUrl]. */
    fun isLinkFortyUrl(uri: Uri): Boolean = isLinkFortyUrl(uri.scheme, uri.host, baseHost, known)

    /**
     * Fetches the list. A 200 replaces the stored list; a 400 or 404 (no such
     * workspace) clears it; any other failure keeps it.
     */
    suspend fun refresh(networkManager: NetworkManagerProtocol) {
        try {
            val response: LinkDomainsResponse = networkManager.request(
                endpoint = "/api/sdk/v1/link-domains",
                method = HttpMethod.GET
            )
            val hosts = response.domains.mapNotNull { it.trim().lowercase().ifEmpty { null } }.toSet()
            known = hosts
            storage.saveLinkDomains(hosts.joinToString("\n"))
            LinkFortyLogger.log("Link domains: ${hosts.joinToString()}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: LinkFortyError.InvalidResponse) {
            if (e.statusCode == 400 || e.statusCode == 404) {
                known = null
                storage.removeLinkDomains()
                LinkFortyLogger.log("No link domains for this app token; every web URL is looked up")
            } else {
                LinkFortyLogger.log("Could not fetch link domains, keeping the stored list: $e")
            }
        } catch (e: Exception) {
            LinkFortyLogger.log("Could not fetch link domains, keeping the stored list: $e")
        } finally {
            fetched.complete(Unit)
        }
    }

    /** Marks the list as settled without a fetch (no app token). */
    fun skipFetch() {
        fetched.complete(Unit)
    }

    /** Waits up to [timeoutMillis] for the first fetch to finish. */
    suspend fun awaitFetch(timeoutMillis: Long) {
        withTimeoutOrNull(timeoutMillis) { fetched.await() }
    }

    companion object {
        private fun parse(stored: String): Set<String> =
            stored.split("\n").mapNotNull { it.trim().lowercase().ifEmpty { null } }.toSet()

        fun hostOf(url: String): String? = runCatching { URI(url).host?.lowercase() }.getOrNull()

        /**
         * A web (http/https) URL is looked up when its host is the SDK's base
         * URL host or one of the [known] link domains, or when the domains are
         * unknown. Any other URL, including custom schemes, is not.
         */
        fun isLinkFortyUrl(scheme: String?, host: String?, baseHost: String?, known: Set<String>?): Boolean {
            val s = scheme?.lowercase()
            if (s != "http" && s != "https") return false
            val h = host?.lowercase() ?: return false
            if (h == baseHost) return true
            return known == null || h in known
        }
    }
}
