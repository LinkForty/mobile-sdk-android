package com.linkforty.sdk.fingerprint

import com.linkforty.sdk.testhelpers.MockFingerprintCollector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for FingerprintCollector behavior via MockFingerprintCollector.
 * Full Android context-dependent tests require instrumentation tests.
 */
class FingerprintCollectorTest {

    private lateinit var sut: MockFingerprintCollector

    @BeforeEach
    fun setUp() {
        sut = MockFingerprintCollector()
    }

    @Test
    fun `collectFingerprint returns expected fields`() {
        val fingerprint = sut.collectFingerprint(
            attributionWindowHours = 168
        )

        assertEquals("TestApp/1.0 Android/13", fingerprint.userAgent)
        assertEquals("America/New_York", fingerprint.timezone)
        assertEquals("en-US", fingerprint.language)
        assertEquals(1080, fingerprint.screenWidth)
        assertEquals(2400, fingerprint.screenHeight)
        assertEquals("Android", fingerprint.platform)
        assertEquals("13", fingerprint.platformVersion)
        assertEquals("1.0.0", fingerprint.appVersion)
        assertEquals(168, fingerprint.attributionWindowHours)
        assertNull(fingerprint.deviceId)
    }

    @Test
    fun `collectFingerprint passes device ID`() {
        val fingerprint = sut.collectFingerprint(
            attributionWindowHours = 24,
            deviceId = "test-gaid"
        )

        assertEquals("test-gaid", fingerprint.deviceId)
        assertEquals(24, fingerprint.attributionWindowHours)
    }

    @Test
    fun `collectFingerprint records call metadata`() {
        sut.collectFingerprint(attributionWindowHours = 72, deviceId = "dev-id")

        assertTrue(sut.collectCalled)
        assertEquals(72, sut.lastAttributionWindow)
        assertEquals("dev-id", sut.lastDeviceId)
    }

    @Test
    fun `user agent format is AppName Version Platform Version`() {
        val fingerprint = sut.collectFingerprint(attributionWindowHours = 168)

        // Format: "AppName/AppVersion Platform/PlatformVersion"
        assertTrue(fingerprint.userAgent.contains("/"))
        assertTrue(fingerprint.userAgent.contains("Android"))
    }

    @Test
    fun `screen dimensions are in pixels`() {
        val fingerprint = sut.collectFingerprint(attributionWindowHours = 168)

        // Should be reasonable pixel values (not dp)
        assertTrue(fingerprint.screenWidth > 0)
        assertTrue(fingerprint.screenHeight > 0)
    }

    @Test
    fun `DeviceFingerprint data class works correctly`() {
        val fp1 = DeviceFingerprint(
            userAgent = "Test/1.0 Android/13",
            timezone = "UTC",
            language = "en",
            screenWidth = 1080,
            screenHeight = 2400,
            platform = "Android",
            platformVersion = "13",
            appVersion = "1.0.0",
            attributionWindowHours = 168
        )
        val fp2 = fp1.copy()

        assertEquals(fp1, fp2)
        assertNotNull(fp1.toString())
    }
}
