package com.example.app.platform

import java.util.TimeZone

/** The JVM's default zone, resolved at the current instant so summer time is included. */
actual fun deviceUtcOffsetMinutes(): Int =
    TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000
