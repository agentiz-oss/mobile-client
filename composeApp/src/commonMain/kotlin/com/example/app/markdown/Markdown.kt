package com.example.app.markdown

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import com.example.app.theme.AppTheme

/**
 * A pocket Markdown reader for text an agent wrote.
 *
 * Agents answer in Markdown whether or not anyone asked them to — a question arrives with `code`
 * spans in it, a run's summary comes as a bulleted list — so the phone either renders it or shows
 * the punctuation raw. What is implemented here is the subset that actually turns up in that
 * output: headings, lists, fenced and inline code, quotes, rules, tables, emphasis and links.
 * Anything outside it (footnotes, HTML, reference links) is left as the literal characters the agent
 * typed, which is the same thing the app did before this file existed — a shape we do not know must
 * never swallow the text it wraps.
 *
 * Hand-written rather than a Markdown dependency for the same reason [com.example.app.diff] parses
 * its own patches: this runs on four targets, and a renderer whose whole job is a dozen line
 * shapes is smaller than the build surface of a library that supports all of CommonMark.
 */

/** One block of a parsed document, in the order it was written. */
internal sealed interface MarkdownBlock {
    /** Free text. Newlines inside it are kept — an agent's line breaks are meant, not incidental. */
    data class Paragraph(val text: String) : MarkdownBlock

    data class Heading(val level: Int, val text: String) : MarkdownBlock

    /**
     * One list item. [marker] is null for a bulleted item and carries the printed number ("2.") for
     * an ordered one — the agent's own numbering, not a re-count, so a list that starts at 3 still
     * starts at 3. [indent] is the nesting depth, capped at [MAX_LIST_INDENT].
     */
    data class ListItem(val indent: Int, val marker: String?, val text: String) : MarkdownBlock

    data class Code(val language: String?, val code: String) : MarkdownBlock

    data class Quote(val text: String) : MarkdownBlock

    data object Divider : MarkdownBlock

    /**
     * A GFM pipe table. Every row is padded to [columns] cells, and a row with *more* cells than
     * the header widens the whole table instead of losing its tail — GFM drops that overflow, but
     * an agent writing a table by hand miscounts a pipe far more often than it means to hide a
     * cell. [alignments] is what the delimiter row asked for, one entry per column.
     */
    data class Table(
        val header: List<String>,
        val rows: List<List<String>>,
        val alignments: List<MarkdownAlign>,
    ) : MarkdownBlock {
        val columns: Int get() = header.size
    }
}

/** What a table's delimiter row (`:---`, `:---:`, `---:`) said about a column. */
enum class MarkdownAlign { Start, Center, End }

/** Deeper nesting than this is rendered flat: a phone runs out of width long before it runs out. */
private const val MAX_LIST_INDENT = 3

private val FENCE = Regex("""^\s{0,3}(```+|~~~+)\s*([^\s`]*)\s*$""")
private val HEADING = Regex("""^\s{0,3}(#{1,6})\s+(.*?)\s*#*\s*$""")
private val DIVIDER = Regex("""^\s{0,3}([-*_])\s*(?:\1\s*){2,}$""")
private val BULLET = Regex("""^(\s*)([-*+])\s+(.*)$""")
private val ORDERED = Regex("""^(\s*)(\d{1,9})([.)])\s+(.*)$""")
private val QUOTE = Regex("""^\s{0,3}>\s?(.*)$""")

/**
 * Whether the line at [index] opens a block of its own, and therefore ends whatever was being
 * accumulated. Takes the whole document because a table is only a table when the *next* line is its
 * delimiter row.
 */
private fun startsBlock(lines: List<String>, index: Int): Boolean {
    val line = lines[index]
    return line.isBlank() ||
        FENCE.matches(line.trimEnd()) ||
        HEADING.matches(line) ||
        DIVIDER.matches(line.trimEnd()) ||
        BULLET.matches(line) ||
        ORDERED.matches(line) ||
        QUOTE.matches(line) ||
        readTable(lines, index) != null
}

/** The document as blocks. Never throws and never drops input: unparsed lines stay paragraphs. */
internal fun parseMarkdown(source: String): List<MarkdownBlock> {
    val lines = source.replace("\r\n", "\n").replace('\r', '\n').split("\n")
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraph = StringBuilder()

    fun flushParagraph() {
        val text = paragraph.toString().trim()
        paragraph.clear()
        if (text.isNotEmpty()) blocks += MarkdownBlock.Paragraph(text)
    }

    var index = 0
    while (index < lines.size) {
        val line = lines[index]

        val fence = FENCE.matchEntire(line.trimEnd())
        if (fence != null) {
            flushParagraph()
            val marker = fence.groupValues[1]
            val body = mutableListOf<String>()
            index++
            while (index < lines.size && !lines[index].trimStart().startsWith(marker)) {
                body += lines[index]
                index++
            }
            // One past the closing fence — or one past the end, when the agent's answer was cut off
            // mid-block. An unterminated fence still renders as code rather than as raw backticks.
            index++
            blocks += MarkdownBlock.Code(fence.groupValues[2].takeIf { it.isNotBlank() }, body.joinToString("\n"))
            continue
        }

        if (line.isBlank()) {
            flushParagraph()
            index++
            continue
        }

        if (DIVIDER.matches(line.trimEnd())) {
            flushParagraph()
            blocks += MarkdownBlock.Divider
            index++
            continue
        }

        val heading = HEADING.matchEntire(line)
        if (heading != null) {
            flushParagraph()
            blocks += MarkdownBlock.Heading(heading.groupValues[1].length, heading.groupValues[2])
            index++
            continue
        }

        val table = readTable(lines, index)
        if (table != null) {
            flushParagraph()
            blocks += table.block
            index = table.end
            continue
        }

        val quote = QUOTE.matchEntire(line)
        if (quote != null) {
            flushParagraph()
            val body = mutableListOf(quote.groupValues[1])
            index++
            while (index < lines.size) {
                val next = QUOTE.matchEntire(lines[index])
                if (next != null) {
                    body += next.groupValues[1]
                } else if (lines[index].isBlank() || startsBlock(lines, index)) {
                    break
                } else {
                    // A wrapped quote whose continuation lost its "> " — the paragraph belongs to
                    // the quote, and reading it as one keeps the bar drawn down the whole passage.
                    body += lines[index]
                }
                index++
            }
            blocks += MarkdownBlock.Quote(body.joinToString("\n").trim())
            continue
        }

        val bullet = BULLET.matchEntire(line)
        val ordered = if (bullet == null) ORDERED.matchEntire(line) else null
        if (bullet != null || ordered != null) {
            flushParagraph()
            val leading = (bullet ?: ordered!!).groupValues[1].length
            val marker = ordered?.let { "${it.groupValues[2]}${it.groupValues[3]}" }
            val body = StringBuilder(if (bullet != null) bullet.groupValues[3] else ordered!!.groupValues[4])
            index++
            // Lazy continuation: an item that wrapped onto the next line is one item, not two.
            while (index < lines.size && !startsBlock(lines, index)) {
                body.append('\n').append(lines[index].trim())
                index++
            }
            blocks += MarkdownBlock.ListItem(
                // Two spaces per level is the usual step, four the other one; both land on a level
                // that only decides how far the item is pushed right.
                indent = (leading / 2).coerceAtMost(MAX_LIST_INDENT),
                marker = marker,
                text = body.toString(),
            )
            continue
        }

        if (paragraph.isNotEmpty()) paragraph.append('\n')
        paragraph.append(line.trim())
        index++
    }
    flushParagraph()
    return blocks
}

private class ParsedTable(val block: MarkdownBlock.Table, val end: Int)

/** A delimiter cell: dashes, with a colon on the side the column is pulled to. */
private val DELIMITER_CELL = Regex("""^:?-+:?$""")

/**
 * The table starting at [index], or null when this is just prose with a pipe in it.
 *
 * Recognised the way GFM does — a header row, then a delimiter row with the same number of cells —
 * because that pair is what makes a table unmistakable. Anything short of it stays a paragraph, so
 * a sentence about `a | b` is never quietly turned into a one-cell table.
 */
private fun readTable(lines: List<String>, index: Int): ParsedTable? {
    if (index + 1 >= lines.size) return null
    if (!lines[index].contains('|')) return null
    val header = splitRow(lines[index])
    if (header.size < 2) return null
    val delimiters = splitRow(lines[index + 1])
    if (delimiters.size != header.size) return null
    if (delimiters.any { !DELIMITER_CELL.matches(it) }) return null

    val rows = mutableListOf<List<String>>()
    var cursor = index + 2
    while (cursor < lines.size) {
        val line = lines[cursor]
        // A table ends at the first line that is not a row of it — including a line that starts
        // some other block and merely happens to carry a pipe.
        if (line.isBlank() || !line.contains('|')) break
        if (HEADING.matches(line) || FENCE.matches(line.trimEnd()) || QUOTE.matches(line)) break
        rows += splitRow(line)
        cursor++
    }

    val columns = maxOf(header.size, rows.maxOfOrNull { it.size } ?: 0)
    fun List<String>.padded() = List(columns) { getOrElse(it) { "" } }
    return ParsedTable(
        MarkdownBlock.Table(
            header = header.padded(),
            rows = rows.map { it.padded() },
            alignments = List(columns) { column ->
                val cell = delimiters.getOrNull(column).orEmpty()
                when {
                    cell.startsWith(':') && cell.endsWith(':') -> MarkdownAlign.Center
                    cell.endsWith(':') -> MarkdownAlign.End
                    else -> MarkdownAlign.Start
                }
            },
        ),
        end = cursor,
    )
}

/**
 * One row's cells. Splits on unescaped pipes and on those only: a pipe inside a code span is rare
 * enough next to `\|`, which is what an agent writes when it means a literal one.
 */
private fun splitRow(line: String): List<String> {
    val cells = mutableListOf<String>()
    val cell = StringBuilder()
    var index = 0
    while (index < line.length) {
        val char = line[index]
        if (char == '\\' && index + 1 < line.length && line[index + 1] == '|') {
            cell.append('|')
            index += 2
            continue
        }
        if (char == '|') {
            cells += cell.toString().trim()
            cell.clear()
            index++
            continue
        }
        cell.append(char)
        index++
    }
    cells += cell.toString().trim()
    // The leading and trailing pipes most tables are written with each produce an empty cell that
    // was never a column; a genuinely empty first or last column keeps its place.
    if (cells.size > 1 && cells.first().isEmpty() && line.trimStart().startsWith("|")) cells.removeAt(0)
    if (cells.size > 1 && cells.last().isEmpty() && line.trimEnd().endsWith("|")) cells.removeAt(cells.size - 1)
    return cells
}

/** Character-level styling shared by every block; sits next to the theme it is drawn from. */
internal object MarkdownSpans {
    val Code = SpanStyle(fontFamily = FontFamily.Monospace, background = AppTheme.Surface)
    val Link = SpanStyle(color = AppTheme.Primary, textDecoration = TextDecoration.Underline)
    val Strong = SpanStyle(fontWeight = FontWeight.SemiBold)
    val Emphasis = SpanStyle(fontStyle = FontStyle.Italic)
    val Strikethrough = SpanStyle(textDecoration = TextDecoration.LineThrough)
}

/** Punctuation a backslash may hide, so `\*` shows a star instead of opening emphasis. */
private const val ESCAPABLE = "\\`*_{}[]()#+-.!~>|"

/** Nesting deeper than this stops being parsed and is appended as typed; pathological input only. */
private const val MAX_INLINE_DEPTH = 6

/**
 * One block's text as styled characters: emphasis, code spans, strikethrough and links.
 *
 * Every construct here is *paired*: an opening marker with no partner is not a marker at all and is
 * appended literally, because half of the text agents write about code contains a lone `*` or `_`.
 */
internal fun markdownInline(text: String): AnnotatedString = buildAnnotatedString { appendInline(text, 0) }

private fun AnnotatedString.Builder.appendInline(text: String, depth: Int) {
    if (depth > MAX_INLINE_DEPTH) {
        append(text)
        return
    }
    val plain = StringBuilder()
    fun flush() {
        if (plain.isNotEmpty()) {
            append(plain.toString())
            plain.clear()
        }
    }

    var index = 0
    while (index < text.length) {
        val char = text[index]

        if (char == '\\' && index + 1 < text.length && text[index + 1] in ESCAPABLE) {
            plain.append(text[index + 1])
            index += 2
            continue
        }

        if (char == '`') {
            val fence = text.runLengthAt(index, '`')
            val close = text.indexOfBacktickRun(index + fence, fence)
            if (close > 0) {
                flush()
                // CommonMark strips one space on each side, so `` `code` `` can hold a backtick.
                val code = text.substring(index + fence, close).removeSurrounding(" ")
                withStyle(MarkdownSpans.Code) { append(code) }
                index = close + fence
                continue
            }
        }

        if (char == '[' || (char == '!' && index + 1 < text.length && text[index + 1] == '[')) {
            val link = text.readLink(index)
            if (link != null) {
                flush()
                // Rendered as a link only when something can open it: an mcp:// target or a file
                // path in brackets is prose on a phone, and a dead tap reads as a broken screen.
                if (link.url.isOpenable()) {
                    withLink(LinkAnnotation.Url(link.url, TextLinkStyles(MarkdownSpans.Link))) {
                        appendInline(link.label, depth + 1)
                    }
                } else {
                    appendInline(link.label, depth + 1)
                }
                index = link.end
                continue
            }
        }

        if (char == '<') {
            val close = text.indexOf('>', index + 1)
            val inner = if (close > 0) text.substring(index + 1, close) else ""
            if (inner.isOpenable() && !inner.any { it.isWhitespace() }) {
                flush()
                withLink(LinkAnnotation.Url(inner, TextLinkStyles(MarkdownSpans.Link))) { append(inner) }
                index = close + 1
                continue
            }
        }

        if (char == 'h' && text.startsWith("http", index) && text.isUrlStart(index)) {
            val end = text.urlEnd(index)
            if (end > index) {
                flush()
                val url = text.substring(index, end)
                withLink(LinkAnnotation.Url(url, TextLinkStyles(MarkdownSpans.Link))) { append(url) }
                index = end
                continue
            }
        }

        if (char == '*' || char == '_' || char == '~') {
            val emphasis = text.readEmphasis(index)
            if (emphasis != null) {
                flush()
                withStyle(emphasis.style) { appendInline(emphasis.content, depth + 1) }
                index = emphasis.end
                continue
            }
        }

        plain.append(char)
        index++
    }
    flush()
}

/** How many times [char] repeats starting at [from]. */
private fun String.runLengthAt(from: Int, char: Char): Int {
    var end = from
    while (end < length && this[end] == char) end++
    return end - from
}

/** The start of the next run of exactly [length] backticks at or after [from], or -1. */
private fun String.indexOfBacktickRun(from: Int, length: Int): Int {
    var index = from
    while (index < this.length) {
        if (this[index] != '`') {
            index++
            continue
        }
        val run = runLengthAt(index, '`')
        if (run == length) return index
        index += run
    }
    return -1
}

private class ParsedLink(val label: String, val url: String, val end: Int)

/**
 * `[label](url)` — and `![alt](url)`, which renders as its alt text plus the link: an image cannot
 * be drawn from an authenticated backend here, and dropping it would lose what it was showing.
 */
private fun String.readLink(start: Int): ParsedLink? {
    val open = if (this[start] == '!') start + 1 else start
    if (open >= length || this[open] != '[') return null
    var depth = 0
    var index = open
    while (index < length) {
        when {
            this[index] == '\\' -> index++
            this[index] == '[' -> depth++
            this[index] == ']' -> {
                depth--
                if (depth == 0) break
            }
        }
        index++
    }
    if (index >= length || depth != 0) return null
    val label = substring(open + 1, index)
    if (index + 1 >= length || this[index + 1] != '(') return null
    var parens = 0
    var cursor = index + 1
    while (cursor < length) {
        when {
            this[cursor] == '\\' -> cursor++
            this[cursor] == '(' -> parens++
            this[cursor] == ')' -> {
                parens--
                if (parens == 0) break
            }
            // A newline inside the target means this was never a link, just brackets in prose.
            this[cursor] == '\n' -> return null
        }
        cursor++
    }
    if (cursor >= length || parens != 0) return null
    // A destination may carry a title — `(url "hint")` — which nothing here has room to show.
    val url = substring(index + 2, cursor).trim().substringBefore(' ').trim('<', '>')
    if (url.isEmpty()) return null
    return ParsedLink(label = label.ifEmpty { url }, url = url, end = cursor + 1)
}

/** Schemes a tap can actually hand to the platform. */
private fun String.isOpenable(): Boolean =
    startsWith("http://") || startsWith("https://") || startsWith("mailto:")

/** A bare URL only starts where a word does — `.https://x` inside a token is not one. */
private fun String.isUrlStart(at: Int): Boolean {
    if (!startsWith("http://", at) && !startsWith("https://", at)) return false
    val before = getOrNull(at - 1) ?: return true
    return !before.isLetterOrDigit() && before != '/' && before != '.'
}

/** Where a bare URL ends: at whitespace, minus the sentence punctuation that followed it. */
private fun String.urlEnd(from: Int): Int {
    var end = from
    while (end < length && !this[end].isWhitespace() && this[end] != '<' && this[end] != '>') end++
    while (end > from && this[end - 1] in ".,;:!?)]}'\"") end--
    return end
}

private class ParsedEmphasis(val style: SpanStyle, val content: String, val end: Int)

/**
 * `**bold**`, `*italic*`, `~~strike~~` and their `_` spellings.
 *
 * `_` is held to word boundaries on both sides, as CommonMark holds it: `agent_run_job` and
 * `spec.source` are names an agent writes constantly, and reading the underscore inside a word as
 * emphasis would eat it. Between words `__x__` is still bold — that is what an author who typed it
 * meant, and a dunder written as prose rather than as code is the rarer case by far.
 */
private fun String.readEmphasis(start: Int): ParsedEmphasis? {
    val marker = this[start]
    val run = runLengthAt(start, marker).coerceAtMost(if (marker == '~') 2 else 3)
    if (marker == '~' && run != 2) return null
    val contentStart = start + run
    if (contentStart >= length || this[contentStart].isWhitespace()) return null
    if (marker == '_' && getOrNull(start - 1)?.isLetterOrDigit() == true) return null

    var index = contentStart
    while (index < length) {
        when {
            this[index] == '\\' -> index++
            this[index] == marker -> {
                val closing = runLengthAt(index, marker)
                val closes = closing >= run &&
                    !this[index - 1].isWhitespace() &&
                    (marker != '_' || getOrNull(index + closing)?.isLetterOrDigit() != true)
                if (closes) {
                    val style = when {
                        marker == '~' -> MarkdownSpans.Strikethrough
                        run >= 3 -> MarkdownSpans.Strong.merge(MarkdownSpans.Emphasis)
                        run == 2 -> MarkdownSpans.Strong
                        else -> MarkdownSpans.Emphasis
                    }
                    // A closing run longer than the opening one closes from its *end*: in
                    // `**очень *важно***` the three stars are the italic's marker plus the bold's,
                    // and only the last two are ours.
                    return ParsedEmphasis(style, substring(contentStart, index + closing - run), index + closing)
                }
                index += closing
                continue
            }
        }
        index++
    }
    return null
}

/**
 * The document with its markup taken off, for the places that show one clipped line of it —
 * a feed row, a run's summary in the task timeline. Those cannot render blocks, and a preview that
 * spends its two lines on `**` and backticks says less than the sentence underneath them.
 */
fun markdownToPlainText(source: String): String =
    parseMarkdown(source).mapNotNull { block ->
        when (block) {
            is MarkdownBlock.Paragraph -> markdownInline(block.text).text
            is MarkdownBlock.Heading -> markdownInline(block.text).text
            is MarkdownBlock.Quote -> markdownInline(block.text).text
            is MarkdownBlock.ListItem -> "${block.marker ?: "•"} ${markdownInline(block.text).text}"
            is MarkdownBlock.Code -> block.code
            is MarkdownBlock.Table -> (listOf(block.header) + block.rows).joinToString("\n") { row ->
                row.joinToString(" | ") { markdownInline(it).text }
            }
            MarkdownBlock.Divider -> null
        }
    }.joinToString("\n").trim()
