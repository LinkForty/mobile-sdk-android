package com.linkforty.sdk.attribution

import com.linkforty.sdk.errors.LinkFortyError
import com.linkforty.sdk.models.DeepLinkData
import com.linkforty.sdk.models.InstallResponse
import com.linkforty.sdk.models.UTMParameters
import com.linkforty.sdk.testhelpers.MockFingerprintCollector
import com.linkforty.sdk.testhelpers.MockNetworkManager
import com.linkforty.sdk.testhelpers.MockStorageManager
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AttributionManagerTest {

    private lateinit var mockNetwork: MockNetworkManager
    private lateinit var mockStorage: MockStorageManager
    private lateinit var mockFingerprint: MockFingerprintCollector
    private lateinit var sut: AttributionManager

    @BeforeEach
    fun setUp() {
        mockNetwork = MockNetworkManager()
        mockStorage = MockStorageManager()
        mockFingerprint = MockFingerprintCollector()
        sut = AttributionManager(mockNetwork, mockStorage, mockFingerprint)
    }

    // -- Install Attribution Tests --

    @Test
    fun `reportInstall sends fingerprint and returns response`() = runTest {
        val expectedResponse = InstallResponse(
            installId = "install-123",
            attributed = true,
            confidenceScore = 92.5,
            matchedFactors = listOf("userAgent", "timezone", "language"),
            deepLinkData = DeepLinkData(
                shortCode = "abc123",
                utmParameters = UTMParameters(source = "facebook")
            )
        )
        mockNetwork.mockResponse = expectedResponse

        val response = sut.reportInstall(attributionWindowHours = 168)

        assertEquals("install-123", response.installId)
        assertTrue(response.attributed)
        assertEquals(92.5, response.confidenceScore)
        assertEquals(3, response.matchedFactors.size)
        assertNotNull(response.deepLinkData)
        assertEquals("abc123", response.deepLinkData?.shortCode)
    }

    @Test
    fun `reportInstall collects fingerprint with correct parameters`() = runTest {
        mockNetwork.mockResponse = InstallResponse(
            installId = "id",
            attributed = false,
            confidenceScore = 0.0,
            matchedFactors = emptyList()
        )

        sut.reportInstall(attributionWindowHours = 24, deviceId = "test-device-id")

        assertTrue(mockFingerprint.collectCalled)
        assertEquals(24, mockFingerprint.lastAttributionWindow)
        assertEquals("test-device-id", mockFingerprint.lastDeviceId)
    }

    @Test
    fun `reportInstall caches install ID`() = runTest {
        mockNetwork.mockResponse = InstallResponse(
            installId = "cached-id",
            attributed = false,
            confidenceScore = 0.0,
            matchedFactors = emptyList()
        )

        sut.reportInstall(attributionWindowHours = 168)

        assertEquals("cached-id", mockStorage.savedInstallId)
    }

    @Test
    fun `reportInstall caches deep link data when attributed`() = runTest {
        val deepLinkData = DeepLinkData(shortCode = "cached-code")
        mockNetwork.mockResponse = InstallResponse(
            installId = "id",
            attributed = true,
            confidenceScore = 85.0,
            matchedFactors = listOf("userAgent"),
            deepLinkData = deepLinkData
        )

        sut.reportInstall(attributionWindowHours = 168)

        assertEquals(deepLinkData, mockStorage.savedInstallData)
    }

    @Test
    fun `reportInstall does not cache deep link data for organic install`() = runTest {
        mockNetwork.mockResponse = InstallResponse(
            installId = "id",
            attributed = false,
            confidenceScore = 0.0,
            matchedFactors = emptyList()
        )

        sut.reportInstall(attributionWindowHours = 168)

        assertNull(mockStorage.savedInstallData)
    }

    @Test
    fun `reportInstall marks has launched`() = runTest {
        mockNetwork.mockResponse = InstallResponse(
            installId = "id",
            attributed = false,
            confidenceScore = 0.0,
            matchedFactors = emptyList()
        )

        sut.reportInstall(attributionWindowHours = 168)

        assertTrue(mockStorage.hasLaunchedCalled)
    }

    @Test
    fun `reportInstall propagates network error`() = runTest {
        mockNetwork.mockError = LinkFortyError.NetworkError(RuntimeException("Connection failed"))

        assertThrows<LinkFortyError.NetworkError> {
            sut.reportInstall(attributionWindowHours = 168)
        }
    }

    @Test
    fun `reportInstall sends to correct endpoint`() = runTest {
        mockNetwork.mockResponse = InstallResponse(
            installId = "id",
            attributed = false,
            confidenceScore = 0.0,
            matchedFactors = emptyList()
        )

        sut.reportInstall(attributionWindowHours = 168)

        assertEquals("/api/sdk/v1/install", mockNetwork.lastEndpoint)
    }

    // -- Data Retrieval Tests --

    @Test
    fun `getInstallId returns stored value`() {
        mockStorage.mockInstallId = "stored-id"
        assertEquals("stored-id", sut.getInstallId())
    }

    @Test
    fun `getInstallId returns null when not stored`() {
        assertNull(sut.getInstallId())
    }

    @Test
    fun `getInstallData returns stored data`() {
        val data = DeepLinkData(shortCode = "stored")
        mockStorage.mockInstallData = data
        assertEquals(data, sut.getInstallData())
    }

    @Test
    fun `isFirstLaunch delegates to storage`() {
        assertTrue(sut.isFirstLaunch())
        mockStorage.mockIsFirstLaunch = false
        assertFalse(sut.isFirstLaunch())
    }

    // -- Data Management Tests --

    @Test
    fun `clearData calls storage clearAll`() {
        sut.clearData()
        assertTrue(mockStorage.clearAllCalled)
    }
}
