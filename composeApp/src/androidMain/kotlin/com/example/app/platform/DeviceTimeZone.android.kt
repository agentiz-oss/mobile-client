package com.example.app.platform

import java.util.TimeZone

/**
 * `TimeZone.getDefault()` follows the system setting, including "set automatically" — Android
 * refreshes it in place when the network hands the phone a new zone.
 *
 * `getOffset(now)` rather than `rawOffset`: the raw one is the zone's standard offset and would
 * read an hour off through summer time.
 */
actual fun deviceUtcOffsetMinutes(): Int =
    TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000
