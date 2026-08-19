package com.example.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.example.app.markdown.MarkdownBlock
import com.example.app.markdown.markdownInline
import com.example.app.markdown.markdownToPlainText
import com.example.app.markdown.parseMarkdown
import com.example.app.data.RunDto
import com.example.app.screens.RunResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the Markdown reader promises about agent output: the shapes agents actually emit are parsed,
 * and everything else survives as the characters that were written — an unpaired marker, an
 * identifier with underscores, a fence nobody closed.
 */
class MarkdownTest {

    @Test
    fun `plain text is one paragraph and keeps its line breaks`() {
        val blocks = parseMarkdown("Готово.\nОсталось выкатить.")
        assertEquals(listOf(MarkdownBlock.Paragraph("Готово.\nОсталось выкатить.")), blocks)
    }

    @Test
    fun `a blank line separates paragraphs`() {
        val blocks = parseMarkdown("Первое.\n\nВторое.")
        assertEquals(2, blocks.size)
        assertTrue(blocks.all { it is MarkdownBlock.Paragraph })
    }

    @Test
    fun `headings carry their level and lose the hashes`() {
        assertEquals(
            listOf(MarkdownBlock.Heading(2, "Итог")),
            parseMarkdown("## Итог"),
        )
    }

    @Test
    fun `a bulleted list keeps one item per line`() {
        val blocks = parseMarkdown("- первое\n- второе")
        assertEquals(
            listOf(
                MarkdownBlock.ListItem(0, null, "первое"),
                MarkdownBlock.ListItem(0, null, "второе"),
            ),
            blocks,
        )
    }

    @Test
    fun `an ordered list keeps the numbering the agent wrote`() {
        val blocks = parseMarkdown("3. третье\n4. четвёртое")
        assertEquals("3.", (blocks[0] as MarkdownBlock.ListItem).marker)
        assertEquals("4.", (blocks[1] as MarkdownBlock.ListItem).marker)
    }

    @Test
    fun `a nested item is one level deeper`() {
        val blocks = parseMarkdown("- верхний\n  - вложенный")
        assertEquals(0, (blocks[0] as MarkdownBlock.ListItem).indent)
        assertEquals(1, (blocks[1] as MarkdownBlock.ListItem).indent)
    }

    @Test
    fun `a wrapped item stays one item`() {
        val blocks = parseMarkdown("- строка,\n  которая перенеслась")
        assertEquals(1, blocks.size)
        assertEquals("строка,\nкоторая перенеслась", (blocks[0] as MarkdownBlock.ListItem).text)
    }

    @Test
    fun `a fenced block keeps its language and its text verbatim`() {
        val blocks = parseMarkdown("Вот:\n```bash\nnpm run build\n  # с отступом\n```\nвсё")
        val code = blocks[1] as MarkdownBlock.Code
        assertEquals("bash", code.language)
        assertEquals("npm run build\n  # с отступом", code.code)
        assertEquals(MarkdownBlock.Paragraph("всё"), blocks[2])
    }

    @Test
    fun `an unclosed fence is still code`() {
        val blocks = parseMarkdown("```\nоборвалось")
        assertEquals(MarkdownBlock.Code(null, "оборвалось"), blocks.single())
    }

    @Test
    fun `markdown inside a fence is not parsed`() {
        val code = parseMarkdown("```\n# not a heading\n- not a list\n```").single() as MarkdownBlock.Code
        assertEquals("# not a heading\n- not a list", code.code)
    }

    @Test
    fun `consecutive quote lines are one block`() {
        val blocks = parseMarkdown("> первая\n> вторая\n\nдальше")
        assertEquals(MarkdownBlock.Quote("первая\nвторая"), blocks[0])
        assertEquals(MarkdownBlock.Paragraph("дальше"), blocks[1])
    }

    @Test
    fun `a rule is its own block`() {
        assertEquals(MarkdownBlock.Divider, parseMarkdown("---").single())
    }

    @Test
    fun `emphasis is styled and its markers are removed`() {
        val bold = markdownInline("**важно**")
        assertEquals("важно", bold.text)
        assertEquals(FontWeight.SemiBold, bold.spanStyles.single().item.fontWeight)

        val italic = markdownInline("*тихо*")
        assertEquals("тихо", italic.text)
        assertEquals(FontStyle.Italic, italic.spanStyles.single().item.fontStyle)

        val struck = markdownInline("~~было~~")
        assertEquals("было", struck.text)
        assertEquals(TextDecoration.LineThrough, struck.spanStyles.single().item.textDecoration)
    }

    @Test
    fun `emphasis nests`() {
        val nested = markdownInline("**очень *важно***")
        assertEquals("очень важно", nested.text)
        assertEquals(2, nested.spanStyles.size)
    }

    @Test
    fun `an unpaired marker stays a character`() {
        assertEquals("5 * 3 и _ сам по себе", markdownInline("5 * 3 и _ сам по себе").text)
    }

    @Test
    fun `underscores inside a word are not emphasis`() {
        assertEquals("agent_run_job и spec_source", markdownInline("agent_run_job и spec_source").text)
    }

    @Test
    fun `underscores between words still emphasise`() {
        val bold = markdownInline("__готово__")
        assertEquals("готово", bold.text)
        assertEquals(FontWeight.SemiBold, bold.spanStyles.single().item.fontWeight)
    }

    @Test
    fun `an escaped marker is printed`() {
        assertEquals("*не курсив*", markdownInline("\\*не курсив\\*").text)
    }

    @Test
    fun `a code span keeps its content unparsed`() {
        val span = markdownInline("см. `spec.stages[].model` тут")
        assertEquals("см. spec.stages[].model тут", span.text)
        assertEquals(1, span.spanStyles.size)
    }

    @Test
    fun `a link shows its label and links its target`() {
        val link = markdownInline("см. [логи](https://agentiz.m42.cx/runs)")
        assertEquals("см. логи", link.text)
        assertEquals(1, link.getLinkAnnotations(0, link.length).size)
    }

    @Test
    fun `a link nobody can open keeps its label as text`() {
        val link = markdownInline("[worker.json](file:///etc/worker.json)")
        assertEquals("worker.json", link.text)
        assertTrue(link.getLinkAnnotations(0, link.length).isEmpty())
    }

    @Test
    fun `a bare url becomes a link without its trailing period`() {
        val bare = markdownInline("открой https://agentiz.m42.cx/runs.")
        assertEquals("открой https://agentiz.m42.cx/runs.", bare.text)
        val annotation = bare.getLinkAnnotations(0, bare.length).single()
        // The sentence's full stop is punctuation, not part of the address.
        assertEquals("https://agentiz.m42.cx/runs", bare.text.substring(annotation.start, annotation.end))
    }

    @Test
    fun `brackets that are not a link are left alone`() {
        assertEquals("массив [0] и (скобки)", markdownInline("массив [0] и (скобки)").text)
    }

    @Test
    fun `a preview drops the markup and keeps the sentence`() {
        assertEquals(
            "Готово\n• собрал npm run build\n• запушил",
            markdownToPlainText("## Готово\n\n- собрал `npm run build`\n- **запушил**"),
        )
    }

    /** The end of the wire: a summary written in Markdown reaches the screen as rendered blocks. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a run's summary renders as markdown, not as its markup`() = runComposeUiTest {
        setContent {
            RunResult(
                RunDto(
                    id = "run-1",
                    status = "succeeded",
                    resultSummary = "## Готово\n\n- собрал `npm run build`\n- **запушил** в main",
                ),
            )
        }

        onNodeWithText("Готово").assertExists()
        onNodeWithText("собрал npm run build").assertExists()
        onNodeWithText("запушил в main").assertExists()
        onNodeWithText("## Готово").assertDoesNotExist()
    }

    @Test
    fun `an empty document renders nothing`() {
        assertEquals(emptyList(), parseMarkdown("   \n\n"))
        assertEquals("", markdownToPlainText(""))
    }
}
