package com.linkforty.sdk.storage

import com.linkforty.sdk.models.DeepLinkData
import com.linkforty.sdk.models.UTMParameters
import com.linkforty.sdk.testhelpers.MockStorageManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for StorageManagerProtocol behavior via MockStorageManager.
 * Full SharedPreferences integration tests require Android instrumentation tests.
 */
class StorageManagerTest {

    private lateinit var sut: MockStorageManager

    @BeforeEach
    fun setUp() {
        sut = MockStorageManager()
    }

    // -- Install ID Tests --

    @Test
    fun `save and retrieve install ID`() {
        val testId = "test-install-id-123"
        sut.mockInstallId = testId
        sut.saveInstallId(testId)

        assertEquals(testId, sut.getInstallId())
        assertEquals(testId, sut.savedInstallId)
    }

    @Test
    fun `get install ID returns null when not set`() {
        assertNull(sut.getInstallId())
    }

    // -- Install Data Tests --

    @Test
    fun `save and retrieve install data`() {
        val testData = DeepLinkData(
            shortCode = "abc123",
            androidURL = "myapp://product/456",
            utmParameters = UTMParameters(source = "facebook", campaign = "summer")
        )

        sut.saveInstallData(testData)
        sut.mockInstallData = testData

        val retrieved = sut.getInstallData()
        assertEquals(testData, retrieved)
        assertEquals("abc123", retrieved?.shortCode)
    }

    @Test
    fun `get install data returns null when not set`() {
        assertNull(sut.getInstallData())
    }

    @Test
    fun `save install data with all fields`() {
        val testData = DeepLinkData(
            shortCode = "test123",
            iosURL = "https://example.com/ios",
            androidURL = "https://example.com/android",
            webURL = "https://example.com/web",
            utmParameters = UTMParameters(
                source = "google",
                medium = "cpc",
                campaign = "spring",
                term = "shoes",
                content = "banner"
            ),
            customParameters = mapOf("productId" to "789", "color" to "blue"),
            clickedAt = "2025-01-15T10:30:00Z",
            linkId = "link-uuid-123"
        )

        sut.saveInstallData(testData)

        assertEquals(testData, sut.savedInstallData)
    }

    // -- First Launch Tests --

    @Test
    fun `isFirstLaunch returns true initially`() {
        assertTrue(sut.isFirstLaunch())
    }

    @Test
    fun `isFirstLaunch returns false after setHasLaunched`() {
        sut.mockIsFirstLaunch = false
        sut.setHasLaunched()

        assertFalse(sut.isFirstLaunch())
        assertTrue(sut.hasLaunchedCalled)
    }

    // -- Clear Data Tests --

    @Test
    fun `clearAll sets flag`() {
        sut.clearAll()
        assertTrue(sut.clearAllCalled)
    }
}
