package com.linkforty.sdk.fingerprint

import java.util.Locale

/**
 * The language a fingerprint reports: `language` or `language-REGION`.
 *
 * `Locale.toLanguageTag()` also carries script, variant and Unicode extension
 * subtags (`en-ES-u-fw-mon-mu-celsius` when the user sets a first day of week or
 * temperature unit, `zh-Hans-CN`). Attribution compares only the language, and
 * the browser side of a click reports `language-REGION`, so those are dropped:
 *
 *   en-ES-u-fw-mon-mu-celsius → en-ES
 *   zh-Hans-CN                → zh-CN
 *   es-419                    → es-419
 *
 * Built from the BCP 47 tag rather than `Locale.language`, which returns legacy
 * codes (`iw`, `in`) on some Android versions.
 */
internal fun fingerprintLanguage(locale: Locale): String = fingerprintLanguage(locale.toLanguageTag())

internal fun fingerprintLanguage(tag: String): String {
    val subtags = tag.split('-', '_')
    val language = subtags.firstOrNull()?.lowercase(Locale.ROOT).orEmpty()
    if (language.isEmpty() || language == "und") return "und"

    for (subtag in subtags.drop(1)) {
        // A single-character subtag starts an extension or private-use section.
        if (subtag.length <= 1) break
        val isRegion = (subtag.length == 2 && subtag.all { it.isLetter() }) ||
            (subtag.length == 3 && subtag.all { it.isDigit() })
        if (isRegion) return "$language-${subtag.uppercase(Locale.ROOT)}"
    }
    return language
}
