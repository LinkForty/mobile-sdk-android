package com.linkforty.e2e

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.linkforty.sdk.LinkForty
import com.linkforty.sdk.models.CreateLinkOptions
import com.linkforty.sdk.models.DeepLinkData
import com.linkforty.sdk.models.LinkFortyConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Records everything the SDK hands the app to `files/lfe2e.jsonl`, which
 * `e2e/run.mjs` reads after each step, and runs the commands the driver appends
 * to `files/lfe2e-commands.jsonl`.
 *
 * Settings come from `files/lfe2e-config.json`, written by the driver after
 * install, and can be overridden for one launch with `lf*` string extras on the
 * launch intent.
 */
class Harness(private val context: Context) {

    private val launchId = UUID.randomUUID().toString()
    private val logFile = File(context.filesDir, "lfe2e.jsonl")
    private val commandFile = File(context.filesDir, "lfe2e-commands.jsonl")
    private val main = Handler(Looper.getMainLooper())

    /**
     * Like `lifecycleScope` in the README: `Dispatchers.Main.immediate`, so
     * `initialize` runs synchronously until its first suspension.
     */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val fileConfig: JSONObject = runCatching {
        JSONObject(File(context.filesDir, "lfe2e-config.json").readText())
    }.getOrDefault(JSONObject())

    private var launchExtras: Map<String, String> = emptyMap()

    private fun setting(key: String): String? =
        launchExtras["lf" + key.replaceFirstChar { it.uppercase() }] ?: fileConfig.optString(key).ifEmpty { null }

    private val order get() = setting("order") ?: "after"

    // -- Log --

    @Synchronized
    fun log(event: String, fields: Map<String, Any?> = emptyMap()) {
        val line = JSONObject()
            .put("event", event)
            .put("launch", launchId)
            .put("at", timestamp())
            .put("thread", if (Looper.myLooper() == Looper.getMainLooper()) "main" else "background")
        fields.forEach { (key, value) -> line.put(key, value ?: JSONObject.NULL) }
        logFile.appendText(line.toString() + "\n")
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())

    private fun json(data: DeepLinkData?): Any? = data?.let {
        JSONObject()
            .put("shortCode", it.shortCode)
            .put("iosUrl", it.iosURL)
            .put("androidUrl", it.androidURL)
            .put("webUrl", it.webURL)
            .put("deepLinkPath", it.deepLinkPath)
            .put("appScheme", it.appScheme)
            .put("clickedAt", it.clickedAt)
            .put("linkId", it.linkId)
            .put("customParameters", it.customParameters?.let { params -> JSONObject(params) })
            .put("utmParameters", it.utmParameters?.let { utm ->
                JSONObject().put("source", utm.source).put("medium", utm.medium).put("campaign", utm.campaign)
            })
    }

    private fun sdkOrNull(): LinkForty? = runCatching { LinkForty.shared }.getOrNull()

    // -- Launch --

    fun start(extras: Map<String, String>) {
        launchExtras = extras
        log("launch", mapOf("extras" to JSONObject(extras), "order" to order))
        log("isFirstLaunchBeforeInit", mapOf("value" to LinkForty.isFirstLaunch(context)))

        if (order == "before") registerCallbacks()

        scope.launch {
            if (order == "concurrent") {
                val first = async { initialize("first") }
                val second = async { initialize("second") }
                first.await()
                second.await()
            } else {
                initialize("only")
            }
            if (order != "before") registerCallbacks()
        }

        startCommandPolling()
    }

    private suspend fun initialize(label: String, baseURL: String? = null) {
        try {
            val response = LinkForty.initialize(context, config(baseURL))
            log(
                "initResult",
                mapOf(
                    "label" to label,
                    "installId" to response.installId,
                    "attributed" to response.attributed,
                    "deepLinkData" to json(response.deepLinkData),
                )
            )
        } catch (e: Exception) {
            log("initError", mapOf("label" to label, "error" to e.toString()))
        }
        log("isFirstLaunchAfterInit", mapOf("value" to LinkForty.isFirstLaunch(context)))
    }

    private fun config(baseURL: String?) = LinkFortyConfig(
        baseURL = baseURL ?: setting("baseURL") ?: "http://localhost:3000",
        apiKey = setting("apiKey"),
        appToken = setting("appToken"),
        debug = true,
        attributionWindowHours = setting("attributionWindowHours")?.toIntOrNull()
            ?: fileConfig.optInt("attributionWindowHours", 168),
    )

    private fun registerCallbacks() {
        try {
            LinkForty.shared.onDeferredDeepLink { data -> log("deferred", mapOf("deepLinkData" to json(data))) }
            LinkForty.shared.onDeepLink { uri, data ->
                log("deepLink", mapOf("url" to uri.toString(), "deepLinkData" to json(data)))
            }
            log("callbacksRegistered")
        } catch (e: Exception) {
            log("callbacksError", mapOf("error" to e.toString()))
        }
    }

    // -- Links --

    /**
     * Hands a link to the SDK the way the README's `MainActivity.handleIntent`
     * does. An exception here would crash an app that follows the README; it is
     * logged instead so the run can report it.
     */
    fun open(uri: Uri) {
        log("openURL", mapOf("url" to uri.toString()))
        try {
            val handled = LinkForty.shared.handleDeepLink(uri)
            log("handleResult", mapOf("url" to uri.toString(), "handled" to handled))
        } catch (e: Exception) {
            log("handleDeepLinkThrew", mapOf("error" to e.toString()))
        }
    }

    // -- Commands --

    private var commandsHandled = 0

    private fun commandLines(): List<String> =
        if (commandFile.exists()) commandFile.readLines().filter { it.isNotBlank() } else emptyList()

    /** Runs commands appended after this launch started. */
    private fun startCommandPolling() {
        commandsHandled = commandLines().size
        val poll = object : Runnable {
            override fun run() {
                val lines = commandLines()
                lines.drop(commandsHandled).forEach { line ->
                    val command = JSONObject(line)
                    val name = command.getString("name")
                    val queryJson = command.optJSONObject("query") ?: JSONObject()
                    val query = queryJson.keys().asSequence().associateWith { queryJson.getString(it) }
                    log("command", mapOf("name" to name, "query" to queryJson))
                    scope.launch { run(name, query) }
                }
                commandsHandled = lines.size
                main.postDelayed(this, 250)
            }
        }
        main.post(poll)
    }

    private suspend fun run(command: String, query: Map<String, String>) {
        val sdk = sdkOrNull()
        when (command) {
            "handle" -> query["url"]?.let { open(Uri.parse(it)) }

            "track" -> {
                val count = query["count"]?.toIntOrNull() ?: 1
                repeat(count) { index ->
                    try {
                        LinkForty.shared.trackEvent(
                            query["name"] ?: "e2e_event",
                            mapOf("index" to index, "source" to "e2e")
                        )
                        log("trackResult", mapOf("ok" to true))
                    } catch (e: Exception) {
                        log("trackResult", mapOf("ok" to false, "error" to e.toString()))
                    }
                }
                log("queued", mapOf("count" to sdkOrNull()?.queuedEventCount))
            }

            "revenue" -> {
                try {
                    LinkForty.shared.trackRevenue(BigDecimal(query["amount"] ?: "9.99"), "USD")
                    log("revenueResult", mapOf("ok" to true))
                } catch (e: Exception) {
                    log("revenueResult", mapOf("ok" to false, "error" to e.toString()))
                }
                log("queued", mapOf("count" to sdkOrNull()?.queuedEventCount))
            }

            "flush" -> {
                try {
                    LinkForty.shared.flushEvents()
                } catch (e: Exception) {
                    log("flushError", mapOf("error" to e.toString()))
                }
                log("queued", mapOf("count" to sdkOrNull()?.queuedEventCount))
            }

            "createLink" -> {
                val params = query["params"]?.let { raw ->
                    val obj = JSONObject(raw)
                    obj.keys().asSequence().associateWith { obj.getString(it) }
                }
                try {
                    val result = LinkForty.shared.createLink(
                        CreateLinkOptions(
                            templateId = query["templateId"],
                            templateSlug = query["templateSlug"],
                            deepLinkParameters = params,
                            title = query["title"],
                            externalUserId = query["externalUserId"],
                        )
                    )
                    log(
                        "createLinkResult",
                        mapOf(
                            "ok" to true,
                            "url" to result.url,
                            "shortCode" to result.shortCode,
                            "linkId" to result.linkId,
                            "deduplicated" to result.deduplicated,
                        )
                    )
                } catch (e: Exception) {
                    log("createLinkResult", mapOf("ok" to false, "error" to e.toString()))
                }
            }

            "state" -> log(
                "state",
                mapOf(
                    "installId" to sdk?.getInstallId(),
                    "installData" to json(sdk?.getInstallData()),
                    "isFirstLaunch" to LinkForty.isFirstLaunch(context),
                    "queued" to sdk?.queuedEventCount,
                )
            )

            "clearData" -> {
                sdk?.clearData()
                log("cleared")
            }

            "reinit" -> initialize("retry", query["baseURL"])

            else -> log("unknownCommand", mapOf("name" to command, "known" to JSONArray()))
        }
    }
}
