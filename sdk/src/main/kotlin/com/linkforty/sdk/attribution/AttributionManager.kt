package com.linkforty.sdk.attribution

import com.linkforty.sdk.LinkFortyLogger
import com.linkforty.sdk.fingerprint.FingerprintCollectorProtocol
import com.linkforty.sdk.models.InstallResponse
import com.linkforty.sdk.network.HttpMethod
import com.linkforty.sdk.network.NetworkManagerProtocol
import com.linkforty.sdk.network.request
import com.linkforty.sdk.storage.StorageManagerProtocol

/**
 * Manages install attribution and deferred deep linking.
 */
internal class AttributionManager(
    private val networkManager: NetworkManagerProtocol,
    private val storageManager: StorageManagerProtocol,
    private val fingerprintCollector: FingerprintCollectorProtocol
) {

    /**
     * Reports an install to the backend and retrieves attribution data.
     *
     * @param attributionWindowHours Attribution window in hours
     * @param deviceId Optional device ID (GAID) if user consented
     * @return Install response with attribution data
     * @throws LinkFortyError on failure
     */
    suspend fun reportInstall(
        attributionWindowHours: Int,
        deviceId: String? = null
    ): InstallResponse {
        // Collect device fingerprint
        val fingerprint = fingerprintCollector.collectFingerprint(
            attributionWindowHours = attributionWindowHours,
            deviceId = deviceId
        )

        LinkFortyLogger.log("Reporting install with fingerprint: $fingerprint")

        // Send install request to backend
        val response: InstallResponse = networkManager.request(
            endpoint = "/api/sdk/v1/install",
            method = HttpMethod.POST,
            body = fingerprint
        )

        LinkFortyLogger.log("Install response: $response")

        // Cache install ID
        storageManager.saveInstallId(response.installId)

        // Cache deep link data if attributed
        if (response.deepLinkData != null) {
            storageManager.saveInstallData(response.deepLinkData)
            LinkFortyLogger.log("Install attributed with confidence: ${response.confidenceScore}%")
        } else {
            LinkFortyLogger.log("Organic install (no attribution)")
        }

        // Mark that app has launched
        storageManager.setHasLaunched()

        return response
    }

    /** Retrieves the install ID. */
    fun getInstallId(): String? = storageManager.getInstallId()

    /** Retrieves the cached install attribution data. */
    fun getInstallData() = storageManager.getInstallData()

    /** Checks if this is the first launch. */
    fun isFirstLaunch(): Boolean = storageManager.isFirstLaunch()

    /** Clears all cached attribution data. */
    fun clearData() {
        storageManager.clearAll()
        LinkFortyLogger.log("Attribution data cleared")
    }
}
