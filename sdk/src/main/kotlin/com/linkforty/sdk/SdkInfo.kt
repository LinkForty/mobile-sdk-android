package com.linkforty.sdk

/**
 * Identifies this SDK (name + version) on outbound requests so the backend can
 * report which SDKs/versions are in use and flag outdated integrations.
 *
 * [VERSION] is sourced from `BuildConfig.SDK_VERSION` (VERSION_NAME in
 * gradle.properties), so it stays in sync with the published artifact version
 * automatically — no manual bump required.
 */
internal object SdkInfo {
    /** SDK platform identifier, sent as `sdkName` and in the `X-LinkForty-SDK` header. */
    const val NAME = "android"

    /** SDK release version, sent as `sdkVersion`. */
    val VERSION: String = BuildConfig.SDK_VERSION
}
