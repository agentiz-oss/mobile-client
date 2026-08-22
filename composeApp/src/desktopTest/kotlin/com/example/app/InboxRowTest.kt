package com.example.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.example.app.data.InboxActionDto
import com.example.app.data.InboxItemDto
import com.example.app.data.RunInstructionDto
import com.example.app.screens.ActionRequiredCard
import com.example.app.screens.InboxRow
import com.example.app.screens.InstructionCard
import com.example.app.screens.formatWaiting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What an inbox row must put in front of a reader: the facts a decision is made on, not the
 * agent's prose, and buttons that act instead of navigating away.
 *
 * Every word on the row except the age is spelled by the server (`lib/inboxItems.ts`), so these
 * tests are about the layout choosing to show them, not about their wording.
 */
class InboxRowTest {

    private val review = InboxItemDto(
        id = "proposal:p1",
        kind = "review",
        activityType = "proposal.waiting_review",
        badge = "ревью",
        headline = "Обновить зависимости",
        facts = "3 файл(ов) · +48/−12 · ветка main · ревизия 2",
        projectId = "prj",
        projectName = "lyapka-rf",
        taskId = "t1",
        taskTitle = "выполни",
        runId = "r1",
        proposalId = "p1",
        revision = 2,
        actions = listOf(
            InboxActionDto(key = "approve", label = "Одобрить…", style = "primary"),
            InboxActionDto(key = "reject", label = "Отклонить…", style = "danger"),
        ),
    )

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a row states the decision's facts and both of its actions`() = runComposeUiTest {
        setContent { InboxRow(item = review, onAction = {}, onOpen = {}) }

        onNodeWithText("Обновить зависимости").assertExists()
        onNodeWithText("3 файл(ов) · +48/−12 · ветка main · ревизия 2").assertExists()
        onNodeWithText("ревью").assertExists()
        // Whose task it is, on its own line — a headline alone does not say which project stalled.
        onNodeWithText("lyapka-rf · выполни").assertExists()
        onNodeWithText("Одобрить…").assertExists()
        onNodeWithText("Отклонить…").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the buttons decide and the row itself navigates`() = runComposeUiTest {
        var acted: String? = null
        var opened = false
        setContent {
            InboxRow(
                item = review,
                onAction = { acted = it.key },
                onOpen = { opened = true },
            )
        }

        onNodeWithText("Одобрить…").performClick()
        assertEquals("approve", acted)
        // Tapping the text of the row is the way to the run behind it — GitHub's inbox rows work
        // the same way, and without it a card with buttons has no way "inside".
        onNodeWithText("Обновить зависимости").performClick()
        assertEquals(true, opened)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `an instruction is shown with its origin, folded when long`() = runComposeUiTest {
        val long = "проверь зависимости. ".repeat(40)
        setContent {
            InstructionCard(RunInstructionDto(source = "comment", body = long, authorName = "Иван"))
        }

        onNodeWithText("Что просили").assertExists()
        onNodeWithText("из комментария · Иван").assertExists()
        onNodeWithText("Показать полностью").assertExists()
    }

    @Test
    fun `the age is coarse and absent for something that just arrived`() {
        val now = 1_800_000_000_000L
        assertEquals("15 мин", formatWaiting(isoOf(now - 15 * 60_000), now))
        assertEquals("2 ч", formatWaiting(isoOf(now - 150 * 60_000), now))
        assertEquals("3 дн", formatWaiting(isoOf(now - 3 * 24 * 60 * 60_000L), now))
        // Under a minute the row simply carries no age; whole minutes is the resolution the ISO
        // string is read at, so anything inside the current minute reads as zero.
        assertNull(formatWaiting(isoOf(now), now))
        assertNull(formatWaiting(null, now))
    }

    /** Minimal ISO-8601 UTC, the shape the API sends. */
    private fun isoOf(epochMillis: Long): String {
        val totalMinutes = epochMillis / 60_000
        val days = totalMinutes / (60 * 24)
        val minutesOfDay = (totalMinutes % (60 * 24)).toInt()
        var year = 1970
        var remaining = days
        while (true) {
            val leap = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
            val length = if (leap) 366 else 365
            if (remaining < length) break
            remaining -= length
            year += 1
        }
        val leap = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
        val lengths = intArrayOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var month = 0
        while (remaining >= lengths[month]) {
            remaining -= lengths[month]
            month += 1
        }
        val day = remaining.toInt() + 1
        fun two(value: Int) = value.toString().padStart(2, '0')
        return "$year-${two(month + 1)}-${two(day)}T${two(minutesOfDay / 60)}:${two(minutesOfDay % 60)}:00Z"
    }
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a row explains what its buttons will do`() = runComposeUiTest {
        // The complaint this answers: «ревью, 0 файлов, кнопка Отклонить — непонятно, что делать».
        val stuck = review.copy(
            kind = "no_changes",
            badge = "без изменений",
            headline = "Запуск ничего не изменил",
            explain = "Одобрять нечего. Папка воркера остаётся занятой, пока её не освободить.",
            actions = listOf(InboxActionDto(key = "reject", label = "Освободить папку")),
        )
        setContent { InboxRow(item = stuck, onAction = {}, onOpen = {}) }

        onNodeWithText("Одобрять нечего. Папка воркера остаётся занятой, пока её не освободить.").assertExists()
        onNodeWithText("Освободить папку").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the card a run leads with carries the whole explanation and acts on it`() = runComposeUiTest {
        var acted: String? = null
        val failed = review.copy(
            kind = "run_failed",
            badge = "ошибка",
            headline = "Worker job queued",
            facts = null,
            explain = "Задача осталась несделанной, и сама она больше ничего не предпримет.",
            actions = listOf(
                InboxActionDto(key = "rerun", label = "Запустить ещё раз", style = "primary"),
                InboxActionDto(key = "close_task", label = "Закрыть задачу", value = "cancelled"),
            ),
        )
        setContent { ActionRequiredCard(item = failed, onAction = { acted = it.value ?: it.key }) }

        onNodeWithText("Worker job queued").assertExists()
        onNodeWithText("Задача осталась несделанной, и сама она больше ничего не предпримет.").assertExists()
        // The status «Закрыть задачу» means is the server's, and the card passes it back untouched.
        onNodeWithText("Закрыть задачу").performClick()
        assertEquals("cancelled", acted)
    }
}
