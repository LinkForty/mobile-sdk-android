package com.linkforty.sdk.storage

import android.content.Context
import android.content.SharedPreferences
import com.linkforty.sdk.LinkFortyLogger
import com.linkforty.sdk.models.DeepLinkData
import com.squareup.moshi.Moshi

/**
 * Protocol for dependency injection in tests.
 */
internal interface StorageManagerProtocol {
    fun saveInstallId(installId: String)
    fun getInstallId(): String?
    fun saveInstallData(data: DeepLinkData)
    fun getInstallData(): DeepLinkData?
    fun isFirstLaunch(): Boolean
    fun setHasLaunched()
    fun clearAll()
}

/**
 * Manages persistent storage for the SDK using SharedPreferences.
 */
internal class StorageManager(
    context: Context,
    private val moshi: Moshi = Moshi.Builder().build()
) : StorageManagerProtocol {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(StorageKeys.PREFS_NAME, Context.MODE_PRIVATE)

    private val deepLinkDataAdapter = moshi.adapter(DeepLinkData::class.java)

    // -- Install ID --

    override fun saveInstallId(installId: String) {
        prefs.edit().putString(StorageKeys.INSTALL_ID, installId).apply()
    }

    override fun getInstallId(): String? {
        return prefs.getString(StorageKeys.INSTALL_ID, null)
    }

    // -- Install Data --

    override fun saveInstallData(data: DeepLinkData) {
        try {
            val json = deepLinkDataAdapter.toJson(data)
            prefs.edit().putString(StorageKeys.INSTALL_DATA, json).apply()
        } catch (e: Exception) {
            LinkFortyLogger.log("Failed to encode install data: ${e.message}")
        }
    }

    override fun getInstallData(): DeepLinkData? {
        val json = prefs.getString(StorageKeys.INSTALL_DATA, null) ?: return null
        return try {
            deepLinkDataAdapter.fromJson(json)
        } catch (e: Exception) {
            LinkFortyLogger.log("Failed to decode install data: ${e.message}")
            null
        }
    }

    // -- First Launch --

    override fun isFirstLaunch(): Boolean {
        return !prefs.getBoolean(StorageKeys.FIRST_LAUNCH, false)
    }

    override fun setHasLaunched() {
        prefs.edit().putBoolean(StorageKeys.FIRST_LAUNCH, true).apply()
    }

    // -- Clear Data --

    override fun clearAll() {
        prefs.edit()
            .remove(StorageKeys.INSTALL_ID)
            .remove(StorageKeys.INSTALL_DATA)
            .remove(StorageKeys.FIRST_LAUNCH)
            .apply()
    }
}
