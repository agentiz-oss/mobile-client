package com.example.app.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.composeunstyled.Text
import com.example.app.theme.AppTheme

/**
 * Agent-written text, rendered as Markdown.
 *
 * A drop-in replacement for a `Text` showing something a model produced: same [style] and [color]
 * arguments, and text with no markup in it comes out looking exactly as it did before. Blocks that
 * need their own shape — code, quotes, lists — build it from [style] rather than from a size of
 * their own, so a caller passing `AppTheme.Label` gets a small block and one passing `AppTheme.Body`
 * a normal one.
 *
 * Not for previews with a line cap: a clipped block layout shows the first block and hides that
 * there were others. Those call [markdownToPlainText] and keep their plain `Text`.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = AppTheme.Body,
    color: Color = AppTheme.Foreground,
) {
    // A live run re-polls every two seconds and hands back the same string; parsing is per text,
    // not per recomposition.
    val blocks = remember(text) { parseMarkdown(text) }
    if (blocks.isEmpty()) return

    Column(modifier = modifier) {
        blocks.forEachIndexed { index, block ->
            if (index > 0) {
                val previous = blocks[index - 1]
                // Items of one list breathe as a list; anything else gets a paragraph's gap.
                val gap = if (block is MarkdownBlock.ListItem && previous is MarkdownBlock.ListItem) 4.dp else 10.dp
                Spacer(Modifier.height(gap))
            }
            key(index) {
                when (block) {
                    is MarkdownBlock.Paragraph ->
                        Text(text = markdownInline(block.text), style = style, color = color)

                    is MarkdownBlock.Heading -> Text(
                        text = markdownInline(block.text),
                        // Scaled off the caller's size so a heading inside a Label-sized block does
                        // not suddenly become a page title.
                        style = style.scaled(headingScale(block.level)).copy(fontWeight = FontWeight.SemiBold),
                        color = color,
                    )

                    is MarkdownBlock.ListItem -> ListItemRow(block, style, color)
                    is MarkdownBlock.Code -> CodeBlock(block, style, color)
                    is MarkdownBlock.Quote -> QuoteBlock(block, style)
                    MarkdownBlock.Divider -> Box(
                        modifier = Modifier.fillMaxWidth().height(1.dp).background(AppTheme.Border),
                    )
                }
            }
        }
    }
}

/**
 * The same style at another size. Both metrics are scaled together so the block keeps its rhythm,
 * and an unspecified one is left alone: multiplying `TextUnit.Unspecified` throws.
 */
private fun TextStyle.scaled(factor: Float): TextStyle = if (factor == 1f) {
    this
} else {
    copy(
        fontSize = if (fontSize.isSpecified) fontSize * factor else fontSize,
        lineHeight = if (lineHeight.isSpecified) lineHeight * factor else lineHeight,
    )
}

/** `#` is a page's own title; by `###` a heading is just bold text with air around it. */
private fun headingScale(level: Int): Float = when (level) {
    1 -> 1.4f
    2 -> 1.2f
    3 -> 1.1f
    else -> 1f
}

@Composable
private fun ListItemRow(item: MarkdownBlock.ListItem, style: TextStyle, color: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(start = (item.indent * 16).dp)) {
        Text(
            text = item.marker ?: "•",
            style = style,
            color = AppTheme.Muted,
            // Fixed width so the text of every item in a list starts on the same column, however
            // wide its own marker is ("9." and "10." must not stagger the list).
            modifier = Modifier.width(if (item.marker != null) 26.dp else 16.dp),
        )
        Text(text = markdownInline(item.text), style = style, color = color, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CodeBlock(block: MarkdownBlock.Code, style: TextStyle, color: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, AppTheme.Border, RoundedCornerShape(8.dp))
            .background(AppTheme.Surface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        block.language?.let {
            Text(text = it, style = AppTheme.Label, color = AppTheme.Muted)
            Spacer(Modifier.height(4.dp))
        }
        Text(
            text = block.code,
            // Code does not reflow: wrapping a command at the screen's edge changes what it says,
            // so the block scrolls sideways instead.
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            style = style.scaled(0.9f).copy(fontFamily = FontFamily.Monospace),
            color = color,
        )
    }
}

@Composable
private fun QuoteBlock(block: MarkdownBlock.Quote, style: TextStyle) {
    // IntrinsicSize.Min lets the bar take exactly the height of the text it marks.
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(modifier = Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(AppTheme.Border))
        Text(
            text = markdownInline(block.text),
            style = style,
            color = AppTheme.Muted,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}
