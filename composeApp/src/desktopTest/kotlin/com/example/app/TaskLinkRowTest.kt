package com.example.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.example.app.screens.TaskLinkRow
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The way out of a run and into the task it belongs to.
 *
 * A run reached from the board, the feed or a notification has nothing but two ids behind it, so
 * this row is the only thing on that page naming what the run was for — and the only route to the
 * description, the discussion and the other attempts. It therefore has to survive a server that
 * sends no title at all: the destination exists either way.
 */
class TaskLinkRowTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `names the task and the project it belongs to`() = runComposeUiTest {
        setContent {
            TaskLinkRow(taskTitle = "Починить пуши на iOS", projectName = "Agentiz", onClick = {})
        }

        onNodeWithText("Починить пуши на iOS").assertExists()
        onNodeWithText("Задача · Agentiz").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `stays a link when the server sent no context`() = runComposeUiTest {
        // What an older server — and a run cached by an older build — answers with.
        setContent { TaskLinkRow(taskTitle = null, projectName = null, onClick = {}) }

        onNodeWithText("Открыть задачу").assertExists()
        onNodeWithText("Задача").assertExists()
        // No project to name, so no dangling separator either.
        onAllNodesWithText("Задача · ").assertCountEquals(0)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `blank strings read as absent, not as an empty title`() = runComposeUiTest {
        setContent { TaskLinkRow(taskTitle = "  ", projectName = "", onClick = {}) }

        onNodeWithText("Открыть задачу").assertExists()
        onNodeWithText("Задача").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the whole row is the tap target`() = runComposeUiTest {
        var opened = 0
        setContent { TaskLinkRow(taskTitle = "Починить пуши на iOS", projectName = "Agentiz", onClick = { opened++ }) }

        // The label above the title is part of the row, not a separate decoration beside it.
        onNodeWithText("Задача · Agentiz").performClick()
        assertEquals(1, opened)

        onNodeWithText("Починить пуши на iOS").performClick()
        assertEquals(2, opened)
    }
}
