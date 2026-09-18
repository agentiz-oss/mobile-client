package com.example.app.platform

import java.util.Locale

/**
 * `Locale.getDefault()` follows the system setting, including a per-app language chosen in
 * Android 13's own settings — which is exactly the switch a reader would expect to work.
 *
 * `toLanguageTag()` rather than `getLanguage()`: the region matters to nobody here, but the tag is
 * the one spelling [com.example.app.i18n.Lang.fromTag] is written against, and `getLanguage()`
 * still answers the obsolete `iw`/`in` codes for two languages we do not have.
 */
actual fun deviceLanguageTag(): String = Locale.getDefault().toLanguageTag()
