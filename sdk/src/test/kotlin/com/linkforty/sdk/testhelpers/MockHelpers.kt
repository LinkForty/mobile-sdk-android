package com.linkforty.sdk.testhelpers

import com.linkforty.sdk.errors.LinkFortyError
import com.linkforty.sdk.fingerprint.DeviceFingerprint
import com.linkforty.sdk.fingerprint.FingerprintCollectorProtocol
import com.linkforty.sdk.models.DeepLinkData
import com.linkforty.sdk.network.HttpMethod
import com.linkforty.sdk.network.NetworkManagerProtocol
import com.linkforty.sdk.storage.StorageManagerProtocol

// -- Mock Network Manager --

class MockNetworkManager : NetworkManagerProtocol {
    var mockResponse: Any? = null
    var mockError: Exception? = null
    var lastEndpoint: String? = null
    var lastMethod: HttpMethod? = null
    var lastBody: Any? = null
    var requestCount: Int = 0

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T> request(
        endpoint: String,
        method: HttpMethod,
        body: Any?,
        headers: Map<String, String>?,
        responseType: Class<T>
    ): T {
        lastEndpoint = endpoint
        lastMethod = method
        lastBody = body
        requestCount++

        mockError?.let { throw it }

        val response = mockResponse
            ?: throw LinkFortyError.InvalidResponse(null, "No mock response")

        return response as? T
            ?: throw LinkFortyError.InvalidResponse(null, "Mock response type mismatch")
    }
}

// -- Mock Storage Manager --

class MockStorageManager : StorageManagerProtocol {
    var savedInstallId: String? = null
    var savedInstallData: DeepLinkData? = null
    var hasLaunchedCalled = false
    var clearAllCalled = false

    var mockInstallId: String? = null
    var mockInstallData: DeepLinkData? = null
    var mockIsFirstLaunch = true

    override fun saveInstallId(installId: String) {
        savedInstallId = installId
    }

    override fun getInstallId(): String? = mockInstallId

    override fun saveInstallData(data: DeepLinkData) {
        savedInstallData = data
    }

    override fun getInstallData(): DeepLinkData? = mockInstallData

    override fun isFirstLaunch(): Boolean = mockIsFirstLaunch

    override fun setHasLaunched() {
        hasLaunchedCalled = true
    }

    override fun clearAll() {
        clearAllCalled = true
    }
}

// -- Mock Fingerprint Collector --

class MockFingerprintCollector : FingerprintCollectorProtocol {
    var collectCalled = false
    var lastAttributionWindow: Int? = null
    var lastDeviceId: String? = null
    var lastAppToken: String? = null

    override fun collectFingerprint(
        attributionWindowHours: Int,
        deviceId: String?,
        appToken: String?
    ): DeviceFingerprint {
        collectCalled = true
        lastAttributionWindow = attributionWindowHours
        lastDeviceId = deviceId
        lastAppToken = appToken

        return DeviceFingerprint(
            userAgent = "TestApp/1.0 Android/13",
            timezone = "America/New_York",
            language = "en-US",
            screenWidth = 1080,
            screenHeight = 2400,
            platform = "Android",
            platformVersion = "13",
            appVersion = "1.0.0",
            deviceId = deviceId,
            attributionWindowHours = attributionWindowHours,
            appToken = appToken
        )
    }
}
