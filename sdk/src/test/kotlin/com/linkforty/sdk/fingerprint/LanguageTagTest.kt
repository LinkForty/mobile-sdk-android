package com.linkforty.sdk.fingerprint

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

class LanguageTagTest {

    @Test
    fun `reduces a locale to language or language-region`() {
        val cases = mapOf(
            "en-US" to "en-US",
            "en-ES-u-fw-mon-mu-celsius" to "en-ES",
            "de-DE-u-mu-fahrenhe" to "de-DE",
            "zh-Hans-CN" to "zh-CN",
            "sr-Latn-RS" to "sr-RS",
            "es-419" to "es-419",
            "en" to "en",
            "fil-PH" to "fil-PH",
            "ca-ES-valencia" to "ca-ES",
            "en-x-private" to "en",
            "he-IL" to "he-IL",
            "id-ID" to "id-ID"
        )
        for ((input, expected) in cases) {
            assertEquals(expected, fingerprintLanguage(Locale.forLanguageTag(input)), input)
        }
    }

    @Test
    fun `normalizes separator and case of a raw tag`() {
        assertEquals("en-GB", fingerprintLanguage("en_GB"))
        assertEquals("en-GB", fingerprintLanguage("EN-gb"))
    }

    @Test
    fun `never exceeds ten characters`() {
        val locale = Locale.forLanguageTag("en-ES-u-ca-gregory-fw-mon-mu-celsius-nu-latn-x-abcdefgh")
        assertTrue(fingerprintLanguage(locale).length <= 10)
    }
}
