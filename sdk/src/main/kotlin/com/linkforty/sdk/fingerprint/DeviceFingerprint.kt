package com.linkforty.sdk.fingerprint

import com.squareup.moshi.JsonClass

/**
 * Device fingerprint for attribution matching.
 */
@JsonClass(generateAdapter = true)
data class DeviceFingerprint(
    /** User-Agent string (e.g., "MyApp/1.0 Android/13") */
    val userAgent: String,

    /** Timezone identifier (e.g., "America/New_York") */
    val timezone: String,

    /** Preferred language (e.g., "en-US") */
    val language: String,

    /** Screen width in pixels */
    val screenWidth: Int,

    /** Screen height in pixels */
    val screenHeight: Int,

    /** Platform name (always "Android") */
    val platform: String = "Android",

    /** Platform version (e.g., "13") */
    val platformVersion: String,

    /** App version (e.g., "1.0.0") */
    val appVersion: String,

    /** Optional device ID (GAID or custom) — only if user consented */
    val deviceId: String? = null,

    /** Attribution window in hours */
    val attributionWindowHours: Int,

    /**
     * Optional public workspace token (LinkForty Cloud only). Lets the
     * server scope organic installs to the right workspace. Moshi omits
     * null values from JSON by default, so legacy/self-hosted servers
     * see the same wire format they always have.
     */
    val appToken: String? = null
)
