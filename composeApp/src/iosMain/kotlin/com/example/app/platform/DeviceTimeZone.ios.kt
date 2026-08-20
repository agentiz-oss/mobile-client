package com.example.app.platform

import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone
import platform.Foundation.secondsFromGMT

/**
 * `localTimeZone` is the auto-updating one — it tracks a change to the system setting, where
 * `systemTimeZone` is a snapshot cached until something calls `resetSystemTimeZone`. Its
 * `secondsFromGMT` is the offset in effect now, so summer time is already in it. Both names
 * come from the `NSExtendedTimeZone` category, which cinterop turns into extension properties —
 * hence an import each, on top of the class's own.
 */
actual fun deviceUtcOffsetMinutes(): Int =
    (NSTimeZone.localTimeZone.secondsFromGMT / 60).toInt()
