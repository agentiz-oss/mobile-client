package com.example.app.platform

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

/**
 * `NSLocale.preferredLanguages` is the ordered list from Settings → General → Language & Region,
 * already intersected by the system with the languages this app ships (its `CFBundleLocalizations`),
 * so its head is the language iOS itself has decided to show this app in. `currentLocale` would
 * answer the *region* format instead — a phone set to English in Spain reports `es_ES` there.
 */
actual fun deviceLanguageTag(): String =
    (NSLocale.preferredLanguages.firstOrNull() as? String).orEmpty()
