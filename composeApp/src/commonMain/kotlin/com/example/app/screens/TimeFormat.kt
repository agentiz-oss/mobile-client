package com.example.app.screens

import com.example.app.platform.deviceUtcOffsetMinutes
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Turning server timestamps into text in the *user's* timezone.
 *
 * Timestamps arrive as ISO-8601 UTC (`2026-08-05T14:32:10Z`) and the app deliberately carries no
 * timezone database: it works from a UTC offset in minutes and plain calendar arithmetic. Lexical
 * ISO order stays chronological, so everything that *sorts* by timestamp keeps using the raw
 * strings — only display goes through here.
 */

/**
 * Which offset the screens render in: the signed-in profile's when the server knows one, otherwise
 * the offset of this device.
 *
 * The device is the fallback rather than raw UTC because the profile timezone
 * ([com.example.app.data.UserDto.utcOffsetMinutes]) comes from Adminizer's optional `UserAP.timezone`
 * column, which no panel screen fills in — in practice the server answers null for everyone, and the
 * app was showing UTC digits beside a phone clock reading something else entirely. The profile still
 * wins when it is set: the server renders push and bell text in that same zone, and two surfaces of
 * one deployment disagreeing about what time a run finished is worse than either choice.
 */
object ViewerTime {
    /** The profile's offset, set from the auth user; null when the server does not know one. */
    var utcOffsetMinutes: Int? = null

    /**
     * How this device's own offset is read — the platform seam, replaced only by tests, which must
     * not depend on the zone of the machine they run on.
     */
    var deviceOffsetMinutes: () -> Int = ::deviceUtcOffsetMinutes

    /** The offset [formatTimestamp] actually applies, resolved fresh at every render. */
    internal fun offsetMinutes(): Int = utcOffsetMinutes ?: deviceOffsetMinutes()
}

/** Days since 1970-01-01 for a civil date (Howard Hinnant's days_from_civil). */
private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
    val y = (if (month <= 2) year - 1 else year).toLong()
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val doy = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097 + doe - 719468
}

/** The inverse: civil (year, month, day) for days since 1970-01-01. */
private fun civilFromDays(days: Long): Triple<Int, Int, Int> {
    val z = days + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = (doy - (153 * mp + 2) / 5 + 1).toInt()
    val month = (if (mp < 10) mp + 3 else mp - 9).toInt()
    val year = yoe + era * 400 + (if (month <= 2) 1 else 0)
    return Triple(year.toInt(), month, day)
}

/** Minutes since the epoch for an ISO-8601 UTC string, or null when it does not parse. */
private fun epochMinutes(iso: String): Long? {
    val datePart = iso.substringBefore('T', missingDelimiterValue = "")
    val timePart = iso.substringAfter('T', missingDelimiterValue = "")
    val dateBits = datePart.split('-')
    if (dateBits.size != 3) return null
    val year = dateBits[0].toIntOrNull() ?: return null
    val month = dateBits[1].toIntOrNull() ?: return null
    val day = dateBits[2].toIntOrNull() ?: return null
    val hour = timePart.take(2).toIntOrNull() ?: return null
    val minute = if (timePart.length >= 5) timePart.substring(3, 5).toIntOrNull() ?: return null else return null
    return daysFromCivil(year, month, day) * 1440 + hour * 60 + minute
}

private fun two(value: Int): String = value.toString().padStart(2, '0')

/**
 * `05.08.2026 17:32` — the instant shifted into the viewer's timezone (see [ViewerTime]). Never
 * answers null for a parseable input: a string carrying a date but no readable time falls through
 * to [fallbackTimestamp] and keeps its UTC digits, as it always did.
 */
internal fun formatTimestamp(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    val minutes = epochMinutes(iso) ?: return fallbackTimestamp(iso)
    val shifted = minutes + ViewerTime.offsetMinutes()
    val days = shifted.floorDiv(1440)
    val ofDay = shifted.mod(1440L).toInt()
    val (year, month, day) = civilFromDays(days)
    return "${two(day)}.${two(month)}.$year ${two(ofDay / 60)}:${two(ofDay % 60)}"
}

/**
 * `05.08.2026 17:32:10` — the same instant as [formatTimestamp], down to the second.
 *
 * Only the run log needs this precision, and it needs it badly: a stage emits dozens of tool calls
 * inside one minute, so a minute-precision stamp says nothing about their order or their spacing.
 * The seconds are read straight from the ISO string — the shift into the viewer's timezone is a
 * whole number of minutes, so it cannot touch them. A timestamp without readable seconds keeps the
 * minute-precision text rather than losing the line's time altogether.
 */
internal fun formatLogTimestamp(iso: String?): String? {
    val minutes = formatTimestamp(iso) ?: return null
    val seconds = iso
        ?.substringAfter('T', missingDelimiterValue = "")
        ?.takeIf { it.length >= 8 && it[5] == ':' }
        ?.substring(6, 8)
        ?.toIntOrNull()
        ?: return minutes
    return "$minutes:${two(seconds)}"
}

/**
 * `1 ч 12 мин` between two UTC instants, `5 мин` under an hour, `<1 мин` when they land in the
 * same minute — the chip beside a finished run. Minute precision is all [epochMinutes] carries,
 * and all a pipeline's timescale deserves. Null when either end is missing or unparseable.
 */
internal fun formatDuration(startIso: String?, endIso: String?): String? {
    if (startIso.isNullOrBlank() || endIso.isNullOrBlank()) return null
    val start = epochMinutes(startIso) ?: return null
    val end = epochMinutes(endIso) ?: return null
    val minutes = end - start
    if (minutes < 0) return null
    val hours = minutes / 60
    return when {
        hours > 0 -> "$hours ч ${minutes % 60} мин"
        minutes == 0L -> "<1 мин"
        else -> "$minutes мин"
    }
}

/** The pre-offset rendering, kept for inputs that carry a date but no parseable time. */
private fun fallbackTimestamp(iso: String): String? {
    val dateBits = iso.substringBefore('T', missingDelimiterValue = "").split('-')
    if (dateBits.size != 3) return null
    val (year, month, day) = dateBits
    return "$day.$month.$year ${iso.substringAfter('T', missingDelimiterValue = "").take(5)}"
}

/** Whole minutes left until a UTC instant; null when it does not parse or has already passed. */
private fun remainingMinutes(untilIso: String?, nowEpochMillis: Long): Long? {
    if (untilIso.isNullOrBlank()) return null
    val until = epochMinutes(untilIso) ?: return null
    val left = until - nowEpochMillis.floorDiv(60_000)
    return if (left > 0) left else null
}

/**
 * `2 ч 15 мин` until the given UTC instant, `45 мин` under an hour, null once it has passed —
 * timezone-free on purpose: a countdown is the same length everywhere.
 */
@OptIn(ExperimentalTime::class)
internal fun formatRemaining(
    untilIso: String?,
    nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
): String? {
    val left = remainingMinutes(untilIso, nowEpochMillis) ?: return null
    val hours = left / 60
    val minutes = left % 60
    return if (hours > 0) "$hours ч $minutes мин" else "$minutes мин"
}

/** Length of a harness session window, mirroring `SESSION_WINDOW_MS` in `lib/harnessAlign.ts`. */
private const val SESSION_WINDOW_MINUTES = 300L

/**
 * `30 полных 5-часовых окон` — how many whole session windows still fit before a long limit window
 * resets. This is the only thing a weekly figure is actually used for: «осталось 154 ч 12 мин» is
 * a number nobody can plan against, and the count of sessions left in it is.
 *
 * Null for a window whose reset is no further out than one session length, which is how the server
 * decides the same question (`sessionWindowOpen` in `lib/harnessAlign.ts` recognizes a session
 * window structurally, by its reset landing within W, rather than by a provider's key). So the
 * 5-hour row never carries a count of itself, and a weekly row down to its last session says
 * nothing instead of «1 окно» — the plain hours read fine at that scale.
 */
@OptIn(ExperimentalTime::class)
internal fun formatFullSessionWindows(
    untilIso: String?,
    nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
): String? {
    val left = remainingMinutes(untilIso, nowEpochMillis) ?: return null
    if (left <= SESSION_WINDOW_MINUTES) return null
    val windows = left / SESSION_WINDOW_MINUTES
    return "$windows ${sessionWindowsNoun(windows)}"
}

/** Russian count agreement: 1 окно, 2–4 окна, 5+ окон — and 11–14 back to окон. */
private fun sessionWindowsNoun(count: Long): String = when {
    count % 100L in 11L..14L -> "полных 5-часовых окон"
    count % 10L == 1L -> "полное 5-часовое окно"
    count % 10L in 2L..4L -> "полных 5-часовых окна"
    else -> "полных 5-часовых окон"
}
