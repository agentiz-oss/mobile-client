package com.example.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.example.app.json.JsonViewer
import com.example.app.json.copyText
import com.example.app.json.countLabel
import com.example.app.json.children
import com.example.app.json.jsonRoot
import com.example.app.json.preview
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The JSON tree's reading rules: what a folded row says about the value behind it, what a copy puts
 * on the clipboard, and — in the composition — that nothing below the root is on screen until
 * somebody opens it.
 */
class JsonViewerTest {

    private fun json(text: String) = Json.parseToJsonElement(text)

    @Test
    fun `a folded object previews its first fields`() {
        assertEquals("{status: \"ok\", files: 3}", json("""{"status":"ok","files":3}""").preview())
    }

    @Test
    fun `a preview stops at its budget instead of wrapping`() {
        val long = json("""{"message":"${"очень длинное сообщение ".repeat(10)}"}""").preview()
        assertTrue(long.length < 100, "preview grew to ${long.length}: $long")
        assertTrue(long.endsWith("…}") || long.endsWith("…\"}"), long)
    }

    @Test
    fun `a preview flattens newlines so a row stays a row`() {
        assertEquals("\"первая вторая\"", json(""""первая\nвторая"""").preview())
    }

    @Test
    fun `empty containers say so rather than showing an ellipsis`() {
        assertEquals("{}", json("{}").preview())
        assertEquals("[]", json("[]").preview())
    }

    @Test
    fun `counts are declined in Russian`() {
        assertEquals("1 поле", json("""{"a":1}""").countLabel())
        assertEquals("3 поля", json("""{"a":1,"b":2,"c":3}""").countLabel())
        assertEquals("5 элементов", json("[1,2,3,4,5]").countLabel())
        assertEquals("11 элементов", json("[${"1,".repeat(10)}1]").countLabel())
        assertEquals("21 элемент", json("[${"1,".repeat(20)}1]").countLabel())
    }

    @Test
    fun `a scalar has no count`() {
        assertEquals(null, json("7").countLabel())
        assertEquals(null, json(""""text"""").countLabel())
    }

    @Test
    fun `copying a string yields its text, not its JSON literal`() {
        val node = jsonRoot(json("""{"errorMessage":"push отклонён:\nno upstream"}""")).children().single()
        assertEquals("push отклонён:\nno upstream", node.element.copyText())
    }

    @Test
    fun `copying a container yields pretty JSON`() {
        val copied = json("""{"a":{"b":1}}""").copyText()
        assertEquals(
            """
            {
                "a": {
                    "b": 1
                }
            }
            """.trimIndent(),
            copied,
        )
    }

    @Test
    fun `copying a number keeps it a number`() {
        assertEquals("3", json("""{"files":3}""").children0().element.copyText())
        assertEquals("null", json("""{"x":null}""").children0().element.copyText())
    }

    private fun kotlinx.serialization.json.JsonElement.children0() = jsonRoot(this).children().first()

    @Test
    fun `a key containing a slash still addresses one node`() {
        val paths = jsonRoot(json("""{"a/b":1,"a":{"b":2}}""")).children().flatMap { child ->
            listOf(child.path) + child.children().map { it.path }
        }
        assertEquals(paths.size, paths.distinct().size, "paths collided: $paths")
    }

    @Test
    fun `array children are addressed by position`() {
        val nodes = jsonRoot(json("""["a","b"]""")).children()
        assertEquals(listOf("[0]", "[1]"), nodes.map { it.label })
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the tree starts folded and opens one level per tap`() = runComposeUiTest {
        setContent {
            JsonViewer(
                element = json("""{"stage":{"role":"reviewer","notes":["почти"]},"ok":true}"""),
                rootLabel = "результат",
            )
        }

        // Only the root row exists; its children's keys are nowhere on screen.
        onNodeWithText("результат").assertExists()
        onAllNodesWithText("stage").assertCountEquals(0)

        onNodeWithText("результат").performClick()
        onNodeWithText("stage").assertExists()
        onNodeWithText("ok").assertExists()
        // …and the grandchild is still folded away.
        onAllNodesWithText("role").assertCountEquals(0)

        onNodeWithText("stage").performClick()
        onNodeWithText("role").assertExists()

        // Folding the root takes the whole subtree with it.
        onNodeWithText("результат").performClick()
        onAllNodesWithText("role").assertCountEquals(0)
        onAllNodesWithText("stage").assertCountEquals(0)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a folded row shows the value beside its key`() = runComposeUiTest {
        setContent {
            JsonViewer(element = json("""{"files":3,"branch":"main"}"""), initiallyExpanded = true)
        }

        onNodeWithText("files").assertExists()
        onNodeWithText("3").assertExists()
        onNodeWithText("\"main\"").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a long array hides its tail behind one row`() = runComposeUiTest {
        val many = (1..80).joinToString(",") { "\"item-$it\"" }
        // 50 rows reach past the bottom of the test window, and a click lands nowhere off-screen —
        // the same scroll the real page provides is what puts the tail row within reach.
        setContent {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                JsonViewer(element = json("[$many]"), initiallyExpanded = true)
            }
        }

        onNodeWithText("\"item-1\"").assertExists()
        onAllNodesWithText("\"item-80\"").assertCountEquals(0)
        onNodeWithText("…показать ещё 30").performScrollTo().performClick()
        onNodeWithText("\"item-80\"").assertExists()
    }
}
