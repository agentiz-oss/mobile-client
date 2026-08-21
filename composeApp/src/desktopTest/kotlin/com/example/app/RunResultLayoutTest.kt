package com.example.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.example.app.data.LogEntryDto
import com.example.app.data.RunDto
import com.example.app.data.StageDto
import com.example.app.platform.deviceUtcOffsetMinutes
import com.example.app.screens.RunResult
import com.example.app.screens.ViewerTime
import com.example.app.screens.logCountLabel
import com.example.app.screens.logLevelCounts
import com.example.app.screens.logLineDetail
import com.example.app.screens.summaryRepeatsStages
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * How a run's page is arranged: the agent's own text once and near the top, the machinery folded
 * away underneath it.
 *
 * The worker builds a run's summary out of the stages' text, so a single-stage pipeline — which is
 * most of them — used to print the same paragraphs twice, with a scrollbox of debug lines between
 * the two copies.
 */
class RunResultLayoutTest {

    private fun stage(role: String, response: String, status: String = "succeeded") = StageDto(
        role = role,
        status = status,
        output = buildJsonObject { put("agentResponse", response) } as JsonObject,
    )

    @Test
    fun `a summary glued together from the stages repeats them`() {
        val stages = listOf(stage("dev", "Собрал и запушил."))
        assertTrue(summaryRepeatsStages("- Собрал и запушил.", stages))
    }

    @Test
    fun `a summary of several stages repeats them too`() {
        val stages = listOf(stage("dev", "Написал код."), stage("review", "Проверил."))
        assertTrue(summaryRepeatsStages("- Написал код.\n- Проверил.", stages))
    }

    @Test
    fun `a summary saying something of its own is kept`() {
        val stages = listOf(stage("dev", "Написал код."))
        assertFalse(summaryRepeatsStages("- Написал код.\n- Осталось выкатить.", stages))
    }

    @Test
    fun `a summary is kept when no stage wrote anything`() {
        assertFalse(summaryRepeatsStages("Готово.", emptyList()))
        assertFalse(summaryRepeatsStages("Готово.", listOf(StageDto(role = "dev", status = "succeeded"))))
    }

    @Test
    fun `the log count is spelled the way it is read`() {
        assertEquals("1 строка", logCountLabel(1))
        assertEquals("3 строки", logCountLabel(3))
        assertEquals("11 строк", logCountLabel(11))
        assertEquals("21 строка", logCountLabel(21))
        assertEquals("112 строк", logCountLabel(112))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the agent's text is shown once, not again as the worker's summary`() = runComposeUiTest {
        setContent {
            RunResult(
                RunDto(
                    id = "run-1",
                    status = "succeeded",
                    stages = listOf(stage("dev", "Собрал и запушил.")),
                    resultSummary = "- Собрал и запушил.",
                ),
            )
        }

        onAllNodesWithText("Собрал и запушил.").assertCountEquals(1)
        onAllNodesWithText("Итог воркера").assertCountEquals(0)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a summary the stages do not account for is still shown`() = runComposeUiTest {
        setContent {
            RunResult(
                RunDto(
                    id = "run-1",
                    status = "succeeded",
                    stages = listOf(stage("dev", "Собрал.")),
                    resultSummary = "- Собрал.\n- Выкатывать вручную.",
                ),
            )
        }

        onNodeWithText("Итог воркера").assertExists()
        onNodeWithText("Выкатывать вручную.").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the log is folded until it is asked for`() = runComposeUiTest {
        setContent {
            RunResult(
                RunDto(
                    id = "run-1",
                    status = "succeeded",
                    logs = listOf(
                        LogEntryDto(level = "info", message = "Worker job queued"),
                        LogEntryDto(level = "debug", message = "tool: read file"),
                    ),
                ),
            )
        }

        onNodeWithText("Лог выполнения").assertExists()
        onNodeWithText("2 строки", substring = true).assertExists()
        onAllNodesWithText("Worker job queued").assertCountEquals(0)

        onNodeWithText("Лог выполнения").performClick()
        onNodeWithText("Worker job queued").assertExists()
        onNodeWithText("tool: read file").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a running run keeps its newest log line in sight while folded`() = runComposeUiTest {
        setContent {
            RunResult(
                RunDto(
                    id = "run-1",
                    status = "running",
                    logs = listOf(
                        LogEntryDto(level = "info", message = "Worker job queued"),
                        LogEntryDto(level = "debug", message = "tool: read file"),
                    ),
                ),
            )
        }

        // The tail only: folded means folded, and the line is there so the page shows a sign of life.
        onNodeWithText("tool: read file").assertExists()
        onAllNodesWithText("Worker job queued").assertCountEquals(0)
    }

    @Test
    fun `the filter offers the levels the trace has, most severe first`() {
        val logs = listOf(
            LogEntryDto(level = "debug", message = "tool: read"),
            LogEntryDto(level = "info", message = "Worker job queued"),
            LogEntryDto(level = "debug", message = "tool: write"),
            LogEntryDto(level = "error", message = "Push failed"),
            LogEntryDto(level = "trace", message = "какой-то новый уровень"),
        )
        assertEquals(
            listOf("error" to 1, "info" to 1, "debug" to 2, "trace" to 1),
            logLevelCounts(logs),
        )
        assertEquals(emptyList(), logLevelCounts(emptyList()))
    }

    @Test
    fun `a tapped line says when it happened, at what level and from which stage`() {
        ViewerTime.utcOffsetMinutes = 180 // Москва
        try {
            assertEquals(
                "05.08.2026 17:32:10 · отладка · dev",
                logLineDetail(
                    LogEntryDto(
                        level = "debug",
                        message = "tool: read",
                        stageRole = "dev",
                        createdAt = "2026-08-05T14:32:10.123Z",
                    ),
                ),
            )
            // No timestamp from the server (an older build, an older cached run) still names a level.
            assertEquals("инфо", logLineDetail(LogEntryDto(level = "info", message = "queued")))
        } finally {
            ViewerTime.utcOffsetMinutes = null
            ViewerTime.deviceOffsetMinutes = ::deviceUtcOffsetMinutes
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the log filters down to one level and back`() = runComposeUiTest {
        setContent {
            RunResult(
                RunDto(
                    id = "run-1",
                    status = "succeeded",
                    logs = listOf(
                        LogEntryDto(level = "info", message = "Worker job queued"),
                        LogEntryDto(level = "debug", message = "tool: read file"),
                    ),
                ),
            )
        }

        onNodeWithText("Лог выполнения").performClick()
        onNodeWithText("отладка", substring = true).performClick()
        onNodeWithText("tool: read file").assertExists()
        onAllNodesWithText("Worker job queued").assertCountEquals(0)

        // The active level is the way back to «все» — tapping it again unfilters.
        onNodeWithText("отладка", substring = true).performClick()
        onNodeWithText("Worker job queued").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a log line opens its time on a tap and not before`() = runComposeUiTest {
        ViewerTime.utcOffsetMinutes = 0
        try {
            setContent {
                RunResult(
                    RunDto(
                        id = "run-1",
                        status = "succeeded",
                        logs = listOf(
                            LogEntryDto(
                                level = "info",
                                message = "Worker job queued",
                                createdAt = "2026-08-05T14:32:10.123Z",
                            ),
                        ),
                    ),
                )
            }

            onNodeWithText("Лог выполнения").performClick()
            onAllNodesWithText("05.08.2026 14:32:10", substring = true).assertCountEquals(0)
            onNodeWithText("Worker job queued").performClick()
            onNodeWithText("05.08.2026 14:32:10", substring = true).assertExists()
        } finally {
            ViewerTime.utcOffsetMinutes = null
            ViewerTime.deviceOffsetMinutes = ::deviceUtcOffsetMinutes
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the raw worker payload is folded away`() = runComposeUiTest {
        setContent {
            RunResult(
                RunDto(
                    id = "run-1",
                    status = "succeeded",
                    workerResult = buildJsonObject { put("status", "succeeded") },
                ),
            )
        }

        onNodeWithText("Полный ответ воркера").assertExists()
        onAllNodesWithText("результат").assertCountEquals(0)

        onNodeWithText("Полный ответ воркера").performClick()
        onNodeWithText("результат").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a failure states its reason above everything else`() = runComposeUiTest {
        setContent {
            RunResult(
                RunDto(
                    id = "run-1",
                    status = "failed",
                    errorMessage = "Workspace is reserved by proposal 42",
                    logs = listOf(LogEntryDto(level = "error", message = "job failed")),
                ),
            )
        }

        onNodeWithText("Workspace is reserved by proposal 42").assertExists()
    }
}
