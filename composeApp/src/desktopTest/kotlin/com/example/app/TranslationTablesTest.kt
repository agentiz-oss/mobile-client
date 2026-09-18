package com.example.app

import com.example.app.i18n.Lang
import com.example.app.i18n.Strings
import com.example.app.i18n.strings
import java.lang.reflect.Method
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What the compiler cannot check about [Strings].
 *
 * That every language has every entry is guaranteed by the interface — a table missing one does not
 * build. What that guarantee does *not* cover is the three ways a translation goes wrong while
 * compiling perfectly: an entry left as the Russian it was copied from, an entry left empty, and an
 * interpolated value dropped on the way into a differently-ordered sentence («осталось $it%» →
 * "% left"). All three produce a screen a reader cannot use and a green build.
 *
 * So this walks the interface reflectively rather than naming entries: an entry added tomorrow is
 * covered without anybody remembering this file exists, which is the only way a check like this
 * survives.
 */
class TranslationTablesTest {

    /**
     * Methods that look up a key rather than interpolate a value: their `String` parameter is a
     * server-side identifier (`"waiting_input"`, `"error"`), so it must **not** appear in the output
     * and an unknown one legitimately answers null. Listed by name with the keys each one is
     * expected to answer for — that list is the actual contract, and an identifier this app stopped
     * understanding would otherwise silently start rendering as itself.
     */
    private val lookups: Map<String, List<String>> = mapOf(
        "taskState" to listOf(
            "new", "queued", "running", "waiting_input", "waiting_review",
            "done", "failed", "cancelled", "ignored",
        ),
        "runState" to listOf("pending", "running", "waiting_input", "succeeded", "failed", "cancelled"),
        "stageState" to listOf("pending", "running", "waiting_input", "succeeded", "failed", "skipped"),
        "logLevel" to listOf("error", "warn", "info", "debug"),
        "subscriptionState" to listOf("available", "exhausted", "unauthorized", "disabled"),
        "workerStatusWord" to listOf("paused", "revoked", "pending"),
        "workerContactState" to listOf("online", "offline", "never_contacted"),
    )

    /**
     * The one lookup that is **total**: a worker's contact state has exactly three values and the
     * third one (`never_contacted`) is spelled as the `else` branch, so there is no identifier it
     * answers null for. Named here rather than given a null-returning branch it would never take.
     */
    private val totalLookups = setOf("workerContactState")

    /**
     * Counts these entries deliberately do not print. `taskCommentAttach` is the button under a
     * comment box — it says whether one file or several are going with it, and the number is already
     * on the strip of files right above it.
     */
    private val countNotPrinted = setOf("taskCommentAttach")

    /**
     * Methods reflection cannot call generically, each for its own reason, and each covered by a
     * hand-written case below instead.
     */
    private val byHand = setOf(
        // `getLang` is the property's getter as Java reflection sees it; the rest take arguments
        // whose meaning a generic caller cannot invent, and have a case of their own below.
        "interactionState", "getLang", "date", "duration", "remaining", "age", "windowsLeft",
    )

    /** A value distinctive enough to find in the output, and lower-case so `.lowercase()` keeps it. */
    private val sample = "zzsamplezz"

    private val tables: List<Strings> = Lang.entries.map { it.table() }

    private fun entries(): List<Method> = Strings::class.java.methods
        .filter { it.declaringClass == Strings::class.java }
        .sortedBy { it.name }

    @Test
    fun `every entry answers in every language`() {
        for (method in entries()) {
            if (method.name in byHand) continue
            if (method.name in lookups) continue
            for (table in tables) {
                val value = method.invoke(table, *argsFor(method)) as String?
                assertNotNull(value, "${table.lang}.${method.name} answered null")
                assertTrue(
                    // A few entries are deliberately a separator plus a word (" · applied …"), so
                    // the check is on content rather than on the untrimmed string.
                    value.isNotBlank(),
                    "${table.lang}.${method.name} is blank",
                )
            }
        }
    }

    @Test
    fun `an interpolated value survives into every language`() {
        for (method in entries()) {
            if (method.name in byHand || method.name in lookups) continue
            if (method.parameterTypes.none { it == String::class.java }) continue
            for (table in tables) {
                val value = method.invoke(table, *argsFor(method)) as String
                assertTrue(
                    sample in value,
                    "${table.lang}.${method.name} dropped its argument: $value",
                )
            }
        }
    }

    @Test
    fun `a count reaches the sentence it is counted in`() {
        for (method in entries()) {
            if (method.name in byHand || method.name in lookups) continue
            if (method.name in countNotPrinted) continue
            if (method.parameterTypes.none { it == Int::class.java || it == Long::class.java }) continue
            for (table in tables) {
                val value = method.invoke(table, *argsFor(method)) as String
                assertTrue(
                    COUNT.toString() in value,
                    "${table.lang}.${method.name} dropped its count: $value",
                )
            }
        }
    }

    @Test
    fun `no русский leaks into the translated tables`() {
        val cyrillic = Regex("\\p{IsCyrillic}")
        for (table in tables) {
            if (table.lang == Lang.Ru) continue
            for (method in entries()) {
                if (method.name in byHand) continue
                val values = lookups[method.name]
                    ?.map { key -> method.invoke(table, key) as String? }
                    ?: listOf(method.invoke(table, *argsFor(method)) as String?)
                for (value in values) {
                    assertTrue(
                        value == null || !cyrillic.containsMatchIn(value),
                        "${table.lang}.${method.name} is still in Russian: $value",
                    )
                }
            }
        }
    }

    @Test
    fun `every state a server can send has a word in every language`() {
        for ((name, keys) in lookups) {
            val method = entries().single { it.name == name }
            for (table in tables) {
                for (key in keys) {
                    val value = method.invoke(table, key) as String?
                    assertNotNull(value, "${table.lang}.$name has no word for '$key'")
                    assertTrue(value.isNotBlank(), "${table.lang}.$name is blank for '$key'")
                }
                // An identifier from a newer server is the caller's to render, not the table's to
                // invent — every call site falls back to the raw key on null.
                if (name !in totalLookups) {
                    assertEquals(null, method.invoke(table, "a-state-from-the-future") as String?)
                }
            }
        }
    }

    @Test
    fun `a question's outcome reads as a sentence in every language`() {
        for (table in tables) {
            val answered = table.interactionState("answered", "accept", "Ivan")
            assertTrue("Ivan" in answered, "${table.lang} lost who answered: $answered")
            for (status in listOf("pending", "expired", "cancelled", "orphaned")) {
                assertTrue(
                    table.interactionState(status, null, null).isNotBlank(),
                    "${table.lang} has no wording for '$status'",
                )
            }
            for (result in listOf("accept", "decline", "cancel")) {
                assertTrue(table.interactionState("answered", result, null).isNotBlank())
            }
        }
    }

    @Test
    fun `dates and durations carry their numbers`() {
        for (table in tables) {
            val date = table.date(2026, 8, 5)
            assertTrue("2026" in date && "5" in date, "${table.lang} lost part of the date: $date")

            assertTrue("12" in table.duration(hours = 1, minutes = 72), "${table.lang}: duration")
            assertTrue(table.duration(hours = 0, minutes = 0).isNotBlank(), "${table.lang}: <1")
            assertTrue("15" in table.remaining(hours = 2, minutes = 15), "${table.lang}: remaining")
            assertTrue("3" in table.age(minutes = 0, hours = 0, days = 3), "${table.lang}: age in days")
            assertTrue("2" in table.age(minutes = 0, hours = 2, days = 0), "${table.lang}: age in hours")
            assertTrue("7" in table.age(minutes = 7, hours = 0, days = 0), "${table.lang}: age in minutes")
        }
    }

    /**
     * The session-window count is the one phrase where the *unit* is interpolated too, and Russian
     * agrees with the number three ways — so every language is asked for the counts that distinguish
     * its forms, and for a window whose length is not a whole number of hours.
     */
    @Test
    fun `whole session windows name their number and their length`() {
        for (table in tables) {
            for (count in listOf(1L, 2L, 5L, 11L, 21L)) {
                val text = table.windowsLeft(count, windowMinutes = 300L)
                assertTrue("$count " in text, "${table.lang}: $count is missing from '$text'")
                assertTrue("5" in text, "${table.lang}: the window's length is missing from '$text'")
            }
            val odd = table.windowsLeft(3L, windowMinutes = 90L)
            assertTrue("90" in odd, "${table.lang}: a 90-minute window reads as '$odd'")
        }
    }

    private fun argsFor(method: Method): Array<Any> = method.parameterTypes.map { type ->
        when (type) {
            String::class.java -> sample
            Int::class.java -> COUNT
            Long::class.java -> COUNT.toLong()
            Boolean::class.java -> true
            else -> error("${method.name} takes a ${type.name}, which this test cannot supply")
        }
    }.toTypedArray()

    private companion object {
        /**
         * Two-digit and not 1, 2 or 5: those are the counts a Russian table's own `when` branches on,
         * so a number outside them proves the count was interpolated rather than matched by luck.
         */
        const val COUNT = 37
    }
}
