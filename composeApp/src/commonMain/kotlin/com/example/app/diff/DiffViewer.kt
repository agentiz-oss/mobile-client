// Adapted from FuwaGit (https://github.com/JamGmilk/FuwaGit), MIT License.
//
// MIT License
//
// Copyright (c) 2026 JamGmilk
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//
// Deviations from upstream, all forced by this codebase rather than preference: material3 and
// string resources are replaced with AppTheme tokens and literal strings (the app has neither),
// the root LazyColumn became a plain Column (the run screen is one verticalScroll column, and a
// nested LazyColumn there cannot measure), and the per-line horizontal scroll became one shared
// ScrollState per file so lines move together while the line-number gutter stays pinned.
package com.example.app.diff

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app.theme.AppTheme

/**
 * FuwaGit's diff palette. The app's own theme is light-only today, so [Light] is what renders;
 * [Dark] is carried so a future dark theme flips one value instead of re-deriving eight colours.
 */
internal data class DiffPalette(
    val addedBackground: Color,
    val addedText: Color,
    val deletedBackground: Color,
    val deletedText: Color,
    val addedHighlight: Color,
    val deletedHighlight: Color,
    val addedInlineBackground: Color,
    val deletedInlineBackground: Color,
) {
    companion object {
        val Light = DiffPalette(
            addedBackground = Color(0xFFDCFFDC),
            addedText = Color(0xFF1A7F37),
            deletedBackground = Color(0xFFFFE0E0),
            deletedText = Color(0xFFCF222E),
            addedHighlight = Color(0xFF80C982),
            deletedHighlight = Color(0xFFFF8181),
            addedInlineBackground = Color(0xFFB4FFB4),
            deletedInlineBackground = Color(0xFFFFB3B3),
        )

        val Dark = DiffPalette(
            addedBackground = Color(0xFF1B3D1B),
            addedText = Color(0xFF73D497),
            deletedBackground = Color(0xFF3D1B1B),
            deletedText = Color(0xFFFF7B7B),
            addedHighlight = Color(0xFF4CAF50),
            deletedHighlight = Color(0xFFE57373),
            addedInlineBackground = Color(0xFF2E7D32),
            deletedInlineBackground = Color(0xFFC62828),
        )
    }
}

/** Code text of a diff line. Sized under [AppTheme.Label] so a 40.dp gutter fits five digits. */
private val LineTextStyle = TextStyle(
    fontSize = 13.sp,
    lineHeight = 18.sp,
    fontFamily = FontFamily.Monospace,
)

/** Line numbers sit in the same 18.sp row as the code, just smaller. */
private val LineNumberStyle = TextStyle(
    fontSize = 11.sp,
    lineHeight = 18.sp,
    fontFamily = FontFamily.Monospace,
)

private val GutterNumberWidth = 40.dp
private val GutterMarkerWidth = 16.dp

/**
 * One file of a parsed patch: its hunks with a pinned line-number gutter and one horizontal
 * scroll shared by every line, so long lines pan together instead of each on its own.
 *
 * Renders the hunks only — the file's name, counters and collapse control belong to whatever
 * card this is embedded in, which already knows how the app draws headers.
 */
@Composable
internal fun DiffViewer(
    fileDiff: FileDiff,
    modifier: Modifier = Modifier,
    palette: DiffPalette = DiffPalette.Light,
) {
    if (fileDiff.isBinary) {
        Indicator("Бинарный файл — содержимое не показывается", modifier)
        return
    }
    if (fileDiff.hunks.isEmpty()) {
        Indicator("Нет строк для показа", modifier)
        return
    }

    val contentScroll = rememberScrollState()
    // Every diff row is forced to exactly one text line of height: the gutter and the code are
    // separate columns, and only equal row heights keep a number aligned with its line.
    val lineHeight = with(LocalDensity.current) { LineTextStyle.lineHeight.toDp() }

    Column(modifier = modifier.fillMaxWidth()) {
        fileDiff.hunks.forEach { hunk ->
            HunkHeader(hunk.header)
            HunkLines(hunk, contentScroll, lineHeight, palette)
        }
    }
}

@Composable
private fun HunkHeader(header: String) {
    BasicText(
        text = header,
        style = LineNumberStyle.copy(color = AppTheme.Muted),
        maxLines = 1,
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.Surface)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
private fun HunkLines(
    hunk: DiffHunk,
    contentScroll: androidx.compose.foundation.ScrollState,
    lineHeight: Dp,
    palette: DiffPalette,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column {
            hunk.lines.forEach { line -> GutterCell(line, lineHeight, palette) }
        }
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            // Lines narrower than the viewport still need their background to reach the right
            // edge; inside a horizontal scroll the column is measured unbounded, so the minimum
            // has to be pinned to the viewport width explicitly.
            val viewportWidth = maxWidth
            Column(modifier = Modifier.horizontalScroll(contentScroll)) {
                hunk.lines.forEach { line -> ContentCell(line, lineHeight, viewportWidth, palette) }
            }
        }
    }
}

private fun lineBackground(lineType: DiffLineType, palette: DiffPalette): Color = when (lineType) {
    DiffLineType.Added -> palette.addedBackground.copy(alpha = 0.4f)
    DiffLineType.Deleted -> palette.deletedBackground.copy(alpha = 0.4f)
    DiffLineType.Context -> Color.Transparent
    DiffLineType.Header -> Color.Transparent
}

private fun lineTextColor(lineType: DiffLineType, palette: DiffPalette): Color = when (lineType) {
    DiffLineType.Added -> palette.addedText
    DiffLineType.Deleted -> palette.deletedText
    DiffLineType.Context -> AppTheme.Foreground
    DiffLineType.Header -> AppTheme.Muted
}

private fun lineMarker(lineType: DiffLineType): String = when (lineType) {
    DiffLineType.Added -> "+"
    DiffLineType.Deleted -> "-"
    else -> " "
}

@Composable
private fun GutterCell(line: DiffLine, lineHeight: Dp, palette: DiffPalette) {
    val textColor = lineTextColor(line.lineType, palette)
    Row(
        modifier = Modifier
            .height(lineHeight)
            .background(lineBackground(line.lineType, palette)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GutterNumber(line.oldLineNumber, textColor)
        GutterNumber(line.newLineNumber, textColor)
        BasicText(
            text = lineMarker(line.lineType),
            style = LineTextStyle.copy(color = textColor),
            maxLines = 1,
            modifier = Modifier.width(GutterMarkerWidth).padding(start = 4.dp),
        )
    }
}

@Composable
private fun GutterNumber(number: Int?, textColor: Color) {
    Box(modifier = Modifier.width(GutterNumberWidth), contentAlignment = Alignment.CenterEnd) {
        BasicText(
            text = number?.toString() ?: "",
            style = LineNumberStyle.copy(color = textColor.copy(alpha = 0.5f)),
            maxLines = 1,
            modifier = Modifier.padding(end = 6.dp),
        )
    }
}

@Composable
private fun ContentCell(line: DiffLine, lineHeight: Dp, viewportWidth: Dp, palette: DiffPalette) {
    val textColor = lineTextColor(line.lineType, palette)
    val text = if (line.inlineDiff != null && line.inlineDiff.hasInlineDiff) {
        inlineDiffText(line.inlineDiff, line.lineType, textColor, palette)
    } else {
        AnnotatedString(line.content, SpanStyle(color = textColor))
    }
    Box(
        modifier = Modifier
            .height(lineHeight)
            .widthIn(min = viewportWidth)
            .background(lineBackground(line.lineType, palette)),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicText(
            text = text,
            style = LineTextStyle,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

/** Word-level emphasis inside a changed line: the changed span is filled, the rest just tinted. */
private fun inlineDiffText(
    inlineDiff: InlineDiff,
    lineType: DiffLineType,
    baseTextColor: Color,
    palette: DiffPalette,
): AnnotatedString = buildAnnotatedString {
    for (segment in inlineDiff.segments) {
        val style = if (segment.isAdded) {
            when (lineType) {
                DiffLineType.Added -> SpanStyle(
                    background = palette.addedHighlight.copy(alpha = 0.6f),
                    color = palette.addedText,
                    fontWeight = FontWeight.Bold,
                )
                DiffLineType.Deleted -> SpanStyle(
                    background = palette.deletedHighlight.copy(alpha = 0.4f),
                    color = baseTextColor,
                    textDecoration = TextDecoration.LineThrough,
                )
                else -> SpanStyle(
                    background = palette.addedHighlight.copy(alpha = 0.3f),
                    color = baseTextColor,
                )
            }
        } else {
            SpanStyle(color = baseTextColor)
        }
        withStyle(style) { append(segment.content) }
    }
}

@Composable
private fun Indicator(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(AppTheme.Surface)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = message, style = AppTheme.Label.copy(color = AppTheme.Muted))
    }
}
