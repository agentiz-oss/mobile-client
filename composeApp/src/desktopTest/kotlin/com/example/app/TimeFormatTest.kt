package com.example.app

import com.example.app.screens.ViewerTime
import com.example.app.screens.formatRemaining
import com.example.app.screens.formatTimestamp
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The display-time contract: UTC wire timestamps shift by the signed-in user's offset — including
 * across a midnight, a month edge and a negative offset — and a deadline reads as hours-and-minutes
 * left, never as a moment already passed.
 */
class TimeFormatTest {

    @AfterTest
    fun reset() {
        ViewerTime.utcOffsetMinutes = null
    }

    @Test
    fun `without an offset the UTC digits show unchanged`() {
        ViewerTime.utcOffsetMinutes = null
        assertEquals("05.08.2026 14:32", formatTimestamp("2026-08-05T14:32:10Z"))
    }

    @Test
    fun `a positive offset shifts the clock`() {
        ViewerTime.utcOffsetMinutes = 180 // Москва
        assertEquals("05.08.2026 17:32", formatTimestamp("2026-08-05T14:32:10.123Z"))
    }

    @Test
    fun `a shift across midnight moves the date too`() {
        ViewerTime.utcOffsetMinutes = 180
        assertEquals("06.08.2026 01:30", formatTimestamp("2026-08-05T22:30:00Z"))
    }

    @Test
    fun `a negative offset can cross a month boundary backwards`() {
        ViewerTime.utcOffsetMinutes = -300 // Нью-Йорк летом… условно
        assertEquals("31.07.2026 23:00", formatTimestamp("2026-08-01T04:00:00Z"))
    }

    @Test
    fun `half-hour zones work`() {
        ViewerTime.utcOffsetMinutes = 330 // Индия
        assertEquals("05.08.2026 20:02", formatTimestamp("2026-08-05T14:32:10Z"))
    }

    @Test
    fun `unparseable input keeps the old behaviour`() {
        assertNull(formatTimestamp("no-date-here"))
        assertNull(formatTimestamp(null))
        assertNull(formatTimestamp(""))
    }

    @Test
    fun `remaining reads in hours and minutes`() {
        val now = 1_770_000_000_000L // some fixed instant
        val inTwoH15 = now + (2 * 60 + 15) * 60_000L
        assertEquals("2 ч 15 мин", formatRemaining(isoAt(inTwoH15), nowEpochMillis = now))
    }

    @Test
    fun `under an hour only minutes show`() {
        val now = 1_770_000_000_000L
        assertEquals("45 мин", formatRemaining(isoAt(now + 45 * 60_000L), nowEpochMillis = now))
    }

    @Test
    fun `a passed deadline yields nothing`() {
        val now = 1_770_000_000_000L
        assertNull(formatRemaining(isoAt(now - 60_000L), nowEpochMillis = now))
    }

    /** Renders an epoch instant as the ISO-8601 UTC string the server would send. */
    private fun isoAt(epochMillis: Long): String =
        java.time.Instant.ofEpochMilli(epochMillis).toString()
}
