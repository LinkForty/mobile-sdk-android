package com.linkforty.sdk.fingerprint

import android.content.Context
import android.os.Build
import android.view.WindowManager
import android.view.WindowMetrics
import java.util.Locale
import java.util.TimeZone

/**
 * Protocol for dependency injection in tests.
 */
internal interface FingerprintCollectorProtocol {
    fun collectFingerprint(
        attributionWindowHours: Int,
        deviceId: String? = null,
        appToken: String? = null
    ): DeviceFingerprint
}

/**
 * Collects device fingerprint data for attribution matching.
 */
internal class FingerprintCollector(
    private val context: Context
) : FingerprintCollectorProtocol {

    override fun collectFingerprint(
        attributionWindowHours: Int,
        deviceId: String?,
        appToken: String?
    ): DeviceFingerprint {
        val (screenWidth, screenHeight) = getScreenDimensions()

        return DeviceFingerprint(
            userAgent = generateUserAgent(),
            timezone = TimeZone.getDefault().id,
            language = Locale.getDefault().toLanguageTag(),
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            platform = "Android",
            platformVersion = Build.VERSION.RELEASE,
            appVersion = getAppVersion(),
            deviceId = deviceId,
            attributionWindowHours = attributionWindowHours,
            appToken = appToken
        )
    }

    /**
     * Generates a User-Agent string.
     * Format: "AppName/AppVersion Android/PlatformVersion"
     */
    private fun generateUserAgent(): String {
        val appName = getAppName()
        val appVersion = getAppVersion()
        val platformVersion = Build.VERSION.RELEASE
        return "$appName/$appVersion Android/$platformVersion"
    }

    /**
     * Returns (width, height) in native pixels.
     * Uses WindowMetrics on API 30+ (non-deprecated), falls back to DisplayMetrics on older APIs.
     */
    private fun getScreenDimensions(): Pair<Int, Int> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics: WindowMetrics = windowManager.maximumWindowMetrics
            val bounds = metrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            Pair(metrics.widthPixels, metrics.heightPixels)
        }
    }

    private fun getAppName(): String {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            "App"
        }
    }

    private fun getAppVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }
}
