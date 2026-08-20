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

/** The pre-offset rendering, kept for inputs that carry a date but no parseable time. */
private fun fallbackTimestamp(iso: String): String? {
    val dateBits = iso.substringBefore('T', missingDelimiterValue = "").split('-')
    if (dateBits.size != 3) return null
    val (year, month, day) = dateBits
    return "$day.$month.$year ${iso.substringAfter('T', missingDelimiterValue = "").take(5)}"
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
    if (untilIso.isNullOrBlank()) return null
    val until = epochMinutes(untilIso) ?: return null
    val left = until - nowEpochMillis.floorDiv(60_000)
    if (left <= 0) return null
    val hours = left / 60
    val minutes = left % 60
    return if (hours > 0) "$hours ч $minutes мин" else "$minutes мин"
}
