package com.example.app.i18n

/**
 * Count agreement, one helper per family of rules the tables need.
 *
 * Kept out of [Strings] on purpose: agreement is a property of a language, not of a string, and a
 * table that inlines the same `when` twenty times is a table where the twenty-first is wrong.
 */

/**
 * Russian: 1 (but not 11), 2–4 (but not 12–14), everything else. The exception in the teens is the
 * whole reason this is not a two-branch `if` — «11 окно» is the classic way to get it wrong.
 */
internal fun ruPlural(count: Long, one: String, few: String, many: String): String = when {
    count % 100L in 11L..14L -> many
    count % 10L == 1L -> one
    count % 10L in 2L..4L -> few
    else -> many
}

internal fun ruPlural(count: Int, one: String, few: String, many: String): String =
    ruPlural(count.toLong(), one, few, many)

/** English and Spanish both split at exactly one, which is the only form either needs here. */
internal fun enPlural(count: Long, one: String, other: String): String =
    if (count == 1L) one else other

internal fun enPlural(count: Int, one: String, other: String): String =
    enPlural(count.toLong(), one, other)
