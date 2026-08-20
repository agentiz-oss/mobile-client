package com.example.app

import com.example.app.platform.deviceUtcOffsetMinutes
import com.example.app.screens.ViewerTime
import com.example.app.screens.formatFullSessionWindows
import com.example.app.screens.formatRemaining
import com.example.app.screens.formatTimestamp
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The display-time contract: UTC wire timestamps shift by the signed-in user's offset — including
 * across a midnight, a month edge and a negative offset — falling back to the device's own zone
 * when the server names none, and a deadline reads as hours-and-minutes left, never as a moment
 * already passed.
 *
 * Every case pins [ViewerTime.deviceOffsetMinutes] rather than letting it reach the real platform:
 * the build machine's zone is not a test input.
 */
class TimeFormatTest {

    @BeforeTest
    fun pinDevice() {
        ViewerTime.deviceOffsetMinutes = { 0 }
    }

    @AfterTest
    fun reset() {
        ViewerTime.utcOffsetMinutes = null
        ViewerTime.deviceOffsetMinutes = ::deviceUtcOffsetMinutes
    }

    @Test
    fun `with no profile offset the device's own zone is used`() {
        ViewerTime.utcOffsetMinutes = null
        ViewerTime.deviceOffsetMinutes = { 420 } // Бангкок
        assertEquals("05.08.2026 21:32", formatTimestamp("2026-08-05T14:32:10Z"))
    }

    @Test
    fun `a profile offset wins over the device's`() {
        ViewerTime.utcOffsetMinutes = 180 // Москва
        ViewerTime.deviceOffsetMinutes = { 420 } // …на телефоне, увезённом в Бангкок
        assertEquals("05.08.2026 17:32", formatTimestamp("2026-08-05T14:32:10Z"))
    }

    @Test
    fun `a device in UTC shows the wire digits unchanged`() {
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

    @Test
    fun `a weekly window counts the whole sessions left in it`() {
        val now = 1_770_000_000_000L
        // Six days and change: 148 h left, of which 29 whole five-hour windows.
        assertEquals(
            "29 полных 5-часовых окон",
            formatFullSessionWindows(isoAt(now + 148 * 3_600_000L), nowEpochMillis = now),
        )
    }

    @Test
    fun `the count agrees with its number`() {
        val now = 1_770_000_000_000L
        fun after(hours: Long) = formatFullSessionWindows(isoAt(now + hours * 3_600_000L), nowEpochMillis = now)
        assertEquals("1 полное 5-часовое окно", after(6))    // 1
        assertEquals("2 полных 5-часовых окна", after(11))   // 2
        assertEquals("5 полных 5-часовых окон", after(26))   // 5
        assertEquals("11 полных 5-часовых окон", after(56))  // 11 — not «11 окно»
        assertEquals("21 полное 5-часовое окно", after(106)) // 21 — back to окно
    }

    @Test
    fun `a session window carries no count of itself`() {
        val now = 1_770_000_000_000L
        // Exactly one window left, and anything shorter: the row is the session window itself.
        assertNull(formatFullSessionWindows(isoAt(now + 5 * 3_600_000L), nowEpochMillis = now))
        assertNull(formatFullSessionWindows(isoAt(now + 90 * 60_000L), nowEpochMillis = now))
    }

    @Test
    fun `no count for a window that has no reset or has passed`() {
        val now = 1_770_000_000_000L
        assertNull(formatFullSessionWindows(null, nowEpochMillis = now))
        assertNull(formatFullSessionWindows("", nowEpochMillis = now))
        assertNull(formatFullSessionWindows("not-a-date", nowEpochMillis = now))
        assertNull(formatFullSessionWindows(isoAt(now - 3_600_000L), nowEpochMillis = now))
    }

    /** Renders an epoch instant as the ISO-8601 UTC string the server would send. */
    private fun isoAt(epochMillis: Long): String =
        java.time.Instant.ofEpochMilli(epochMillis).toString()
}
