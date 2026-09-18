package com.example.app.platform

/**
 * The browser's own preference (`navigator.language`) — what the user set in the browser, which is
 * not necessarily the OS language. Guarded like every other JS reach here: a context without a
 * `navigator` answers an empty string rather than throwing on the first read.
 */
actual fun deviceLanguageTag(): String = runCatching { jsNavigatorLanguage() }.getOrDefault("")

private fun jsNavigatorLanguage(): String = js("(navigator && navigator.language) || ''")
