package com.example.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.example.app.data.DiffDto
import com.example.app.data.DiffStatsDto
import com.example.app.data.RunDto
import com.example.app.screens.RunResult
import kotlin.test.Test

/**
 * The «Изменения» section's contract: it appears exactly when the run carries a diff, wears the
 * same badges as the dashboard, and unfolds one file at a time so a many-file patch stays a list.
 */
class DiffSectionTest {

    private val twoFilePatch = listOf(
        "diff --git a/first.kt b/first.kt",
        "index 1111111..2222222 100644",
        "--- a/first.kt",
        "+++ b/first.kt",
        "@@ -1,2 +1,3 @@",
        " shared context",
        "+first file addition",
        " tail",
        "diff --git a/second.kt b/second.kt",
        "index 3333333..4444444 100644",
        "--- a/second.kt",
        "+++ b/second.kt",
        "@@ -1 +1,2 @@",
        " other context",
        "+second file addition",
        "",
    ).joinToString("\n")

    private fun runWith(diff: DiffDto?) = RunDto(id = "run-1", status = "succeeded", diff = diff)

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `section renders badges, opens the first file and folds the rest`() = runComposeUiTest {
        setContent {
            RunResult(
                runWith(
                    DiffDto(
                        patch = twoFilePatch,
                        stats = DiffStatsDto(files = 2, insertions = 2, deletions = 0),
                        baseSha = "a1b2c3d4e5f6a7b8",
                    ),
                ),
            )
        }

        onNodeWithText("Изменения").assertExists()
        onNodeWithText("от a1b2c3d4e5f6 · 2 файл(ов), ", substring = true).assertExists()
        onNodeWithText("в репозиторий не отправлено", substring = true).assertExists()

        // First file open, second folded to its header.
        onNodeWithText("first file addition").assertExists()
        onNodeWithText("second.kt").assertExists()
        onAllNodesWithText("second file addition").assertCountEquals(0)

        onNodeWithText("second.kt").performClick()
        onNodeWithText("second file addition").assertExists()

        // Folding the first file back removes its body.
        onNodeWithText("first.kt").performClick()
        onAllNodesWithText("first file addition").assertCountEquals(0)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `truncated and applied badges`() = runComposeUiTest {
        setContent {
            RunResult(
                runWith(
                    DiffDto(
                        patch = twoFilePatch,
                        truncated = true,
                        appliedAt = "2026-08-15T14:32:10Z",
                        appliedCommitSha = "deadbeef1234567890",
                    ),
                ),
            )
        }

        onNodeWithText("Патч обрезан по лимиту размера — показана часть изменений.").assertExists()
        onNodeWithText("применено 15.08.2026 14:32, коммит deadbeef1234", substring = true).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `no diff - no section`() = runComposeUiTest {
        setContent { RunResult(runWith(null)) }
        onAllNodesWithText("Изменения").assertCountEquals(0)
    }
}
