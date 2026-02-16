package com.linkforty.sdk

import android.util.Log

/**
 * Internal logger for the LinkForty SDK.
 * Only logs when debug mode is enabled.
 */
internal object LinkFortyLogger {

    private const val TAG = "LinkForty"

    @Volatile
    var isDebugEnabled: Boolean = false

    fun log(message: String) {
        if (isDebugEnabled) {
            Log.d(TAG, message)
        }
    }
}
