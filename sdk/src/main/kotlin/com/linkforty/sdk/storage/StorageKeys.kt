package com.linkforty.sdk.storage

/**
 * SharedPreferences keys used by the SDK.
 */
internal object StorageKeys {
    /** Prefix for all LinkForty SDK keys */
    private const val PREFIX = "com.linkforty.sdk"

    /** SharedPreferences file name */
    const val PREFS_NAME = "$PREFIX.prefs"

    /** Install ID key */
    const val INSTALL_ID = "$PREFIX.installId"

    /** Install data key (DeepLinkData JSON) */
    const val INSTALL_DATA = "$PREFIX.installData"

    /** First launch flag key */
    const val FIRST_LAUNCH = "$PREFIX.firstLaunch"
}
