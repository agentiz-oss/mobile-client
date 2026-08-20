package com.example.app.platform

/**
 * The browser's own zone. JavaScript's `getTimezoneOffset()` counts minutes *behind* UTC — it
 * answers −180 in Москва — so the sign is flipped to the east-positive convention used everywhere
 * else here.
 */
actual fun deviceUtcOffsetMinutes(): Int = jsTimezoneOffsetMinutes()

private fun jsTimezoneOffsetMinutes(): Int = js("-new Date().getTimezoneOffset()")
