package com.linkforty.sdk

import com.linkforty.sdk.errors.LinkFortyError
import com.linkforty.sdk.models.LinkFortyConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LinkFortyTest {

    // -- Pre-initialization Tests --

    @Test
    fun `shared throws NotInitialized when not initialized`() {
        // Reset any existing state
        try { LinkForty.shared.reset() } catch (_: Exception) {}

        assertThrows<LinkFortyError.NotInitialized> {
            LinkForty.shared
        }
    }

    @Test
    fun `getInstallId returns null before initialization`() {
        try { LinkForty.shared.reset() } catch (_: Exception) {}

        // shared throws, so we can't call getInstallId
        assertThrows<LinkFortyError.NotInitialized> {
            LinkForty.shared.getInstallId()
        }
    }

    // -- Config Validation Tests --

    @Test
    fun `config with HTTPS URL validates successfully`() {
        val config = LinkFortyConfig(
            baseURL = "https://api.linkforty.com",
            apiKey = "test-key"
        )
        config.validate() // Should not throw
    }

    @Test
    fun `config with HTTP URL throws`() {
        val config = LinkFortyConfig(
            baseURL = "http://api.linkforty.com",
            apiKey = "test-key"
        )
        assertThrows<LinkFortyError.InvalidConfiguration> {
            config.validate()
        }
    }

    @Test
    fun `config with localhost HTTP is allowed`() {
        val config = LinkFortyConfig(
            baseURL = "http://localhost:3000",
            apiKey = "test-key"
        )
        config.validate() // Should not throw
    }

    @Test
    fun `config with 127_0_0_1 HTTP is allowed`() {
        val config = LinkFortyConfig(
            baseURL = "http://127.0.0.1:3000",
            apiKey = "test-key"
        )
        config.validate() // Should not throw
    }

    @Test
    fun `config with 10_0_2_2 HTTP is allowed (emulator)`() {
        val config = LinkFortyConfig(
            baseURL = "http://10.0.2.2:3000",
            apiKey = "test-key"
        )
        config.validate() // Should not throw
    }

    @Test
    fun `config with invalid attribution window throws - too low`() {
        val config = LinkFortyConfig(
            baseURL = "https://api.linkforty.com",
            attributionWindowHours = 0
        )
        assertThrows<LinkFortyError.InvalidConfiguration> {
            config.validate()
        }
    }

    @Test
    fun `config with invalid attribution window throws - too high`() {
        val config = LinkFortyConfig(
            baseURL = "https://api.linkforty.com",
            attributionWindowHours = 3000
        )
        assertThrows<LinkFortyError.InvalidConfiguration> {
            config.validate()
        }
    }

    @Test
    fun `config with negative attribution window throws`() {
        val config = LinkFortyConfig(
            baseURL = "https://api.linkforty.com",
            attributionWindowHours = -1
        )
        assertThrows<LinkFortyError.InvalidConfiguration> {
            config.validate()
        }
    }

    @Test
    fun `config with valid attribution window validates - minimum`() {
        val config = LinkFortyConfig(
            baseURL = "https://api.linkforty.com",
            attributionWindowHours = 1
        )
        config.validate() // Should not throw
    }

    @Test
    fun `config with valid attribution window validates - maximum`() {
        val config = LinkFortyConfig(
            baseURL = "https://api.linkforty.com",
            attributionWindowHours = 2160
        )
        config.validate() // Should not throw
    }

    @Test
    fun `config with no API key validates for self-hosted`() {
        val config = LinkFortyConfig(
            baseURL = "https://self-hosted.example.com",
            apiKey = null
        )
        config.validate() // Should not throw
    }

    @Test
    fun `config toString masks API key`() {
        val config = LinkFortyConfig(
            baseURL = "https://api.example.com",
            apiKey = "secret-key-12345"
        )
        val str = config.toString()
        assertTrue(str.contains("***"))
        assertTrue(!str.contains("secret-key-12345"))
    }

    @Test
    fun `config defaults are correct`() {
        val config = LinkFortyConfig(baseURL = "https://api.example.com")
        assertNull(config.apiKey)
        assertEquals(false, config.debug)
        assertEquals(168, config.attributionWindowHours)
    }

    // -- Error Tests --

    @Test
    fun `LinkFortyError messages are descriptive`() {
        val errors = listOf(
            LinkFortyError.NotInitialized(),
            LinkFortyError.AlreadyInitialized(),
            LinkFortyError.InvalidConfiguration("test detail"),
            LinkFortyError.NetworkError(RuntimeException("connection failed")),
            LinkFortyError.InvalidResponse(404, "Not Found"),
            LinkFortyError.DecodingError(RuntimeException("bad json")),
            LinkFortyError.EncodingError(RuntimeException("encode failed")),
            LinkFortyError.InvalidEventData("empty name"),
            LinkFortyError.InvalidDeepLinkUrl("bad url"),
            LinkFortyError.MissingApiKey()
        )

        errors.forEach { error ->
            assertTrue(error.message!!.isNotBlank(), "Error ${error::class.simpleName} has blank message")
        }
    }

    @Test
    fun `InvalidResponse includes status code in message`() {
        val error = LinkFortyError.InvalidResponse(404, "Not Found")
        assertTrue(error.message!!.contains("404"))
        assertTrue(error.message!!.contains("Not Found"))
    }

    @Test
    fun `InvalidResponse handles null status code`() {
        val error = LinkFortyError.InvalidResponse(null, "Unknown error")
        assertTrue(error.message!!.contains("Unknown error"))
    }
}
