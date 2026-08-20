package com.example.app.platform

/**
 * This device's current offset from UTC, in minutes east of Greenwich (Москва → 180).
 *
 * Deliberately an *offset* and not a zone name: the app carries no timezone database, and every
 * platform below can answer "how far from UTC am I right now" from the OS with no data of its own.
 * Read at the moment a timestamp is rendered rather than cached, so DST and a phone crossing a
 * border both take effect without a restart.
 */
expect fun deviceUtcOffsetMinutes(): Int
