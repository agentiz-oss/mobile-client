package com.example.app.json

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.example.app.components.CheckIcon
import com.example.app.components.CopyIcon
import com.example.app.components.DownIcon
import com.example.app.components.ForwardIcon
import com.example.app.components.rememberClipboardWriter
import com.example.app.platform.hapticActionComplete
import com.example.app.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * A payload of unknown schema, shown as a tree that starts folded.
 *
 * The rule this replaces was "print the whole pretty JSON into a scroll box": correct, and unusable
 * on a phone — a worker result of a few hundred lines buried the rest of the page and answered no
 * question without scrolling. Here every branch is one row until somebody opens it, so the first
 * screen is the payload's *shape*, and depth is paid for only where a person asks for it.
 *
 * Copying is per row, not per document: the reason to open `errorMessage` is usually to paste it
 * somewhere, and the root row's own button is what copies the whole thing — same affordance, one
 * scope up. What lands on the clipboard is [copyText], so a string comes out as its text and
 * anything else as valid JSON.
 *
 * [initiallyExpanded] opens the root's own children (never deeper): worth it where the payload is
 * known to be small and the fold would be pure friction.
 */
@Composable
fun JsonViewer(
    element: JsonElement,
    modifier: Modifier = Modifier,
    rootLabel: String? = null,
    initiallyExpanded: Boolean = false,
) {
    val root = remember(element, rootLabel) { jsonRoot(element, rootLabel) }
    // Keyed by path, not by node: a poll that replaces the payload with an equal-but-new element
    // must not fold up the branch somebody is reading.
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val expandedAll = remember { mutableStateMapOf<String, Boolean>() }
    var copiedPath by remember { mutableStateOf<String?>(null) }
    val copy = rememberClipboardWriter()

    LaunchedEffect(copiedPath) {
        if (copiedPath != null) {
            delay(COPIED_FLASH_MILLIS)
            copiedPath = null
        }
    }

    // The default lives in the lookup rather than as a seeded entry, so a person folding the root
    // back stays folded — a seeded `true` would be re-applied on every recomposition.
    val isOpen: (String) -> Boolean = { path -> expanded[path] ?: (initiallyExpanded && path == root.path) }

    // Rebuilt on every composition rather than remembered: the fold state is read inside
    // `buildRows`, which is what subscribes this composable to it — a `remember` keyed on a copy of
    // the maps registers no such read and leaves an opened branch unopened.
    val rows = buildRows(root, isExpanded = isOpen, showsAll = { expandedAll[it] == true })

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, AppTheme.Border, RoundedCornerShape(AppTheme.Radius))
            .background(AppTheme.Surface, RoundedCornerShape(AppTheme.Radius))
            .padding(vertical = 4.dp),
    ) {
        rows.forEach { row ->
            when (row) {
                is JsonRow.Value -> JsonNodeRow(
                    node = row.node,
                    expanded = isOpen(row.node.path),
                    copied = copiedPath == row.node.path,
                    onToggle = { expanded[row.node.path] = !isOpen(row.node.path) },
                    onCopy = {
                        copy(row.node.element.copyText())
                        copiedPath = row.node.path
                        hapticActionComplete()
                    },
                )
                is JsonRow.More -> MoreRow(row) { expandedAll[row.parentPath] = true }
            }
        }
    }
}

private const val COPIED_FLASH_MILLIS = 1600L

/**
 * How many children of one container are drawn before the rest go behind "показать все".
 *
 * A log array can hold thousands of entries, and this is a plain Column inside a page that already
 * scrolls — a lazy list here would fight that scroll, so the cap is what keeps an accidental tap on
 * a huge array from building thousands of rows.
 */
private const val CHILDREN_LIMIT = 50

/** A drawn row: either a node, or the tail marker of a container cut off by [CHILDREN_LIMIT]. */
private sealed interface JsonRow {
    data class Value(val node: JsonNode) : JsonRow

    data class More(val parentPath: String, val hidden: Int, val depth: Int) : JsonRow
}

/** Flattens the currently open part of the tree, depth-first, in document order. */
private fun buildRows(
    root: JsonNode,
    isExpanded: (String) -> Boolean,
    showsAll: (String) -> Boolean,
): List<JsonRow> {
    val rows = mutableListOf<JsonRow>()

    fun visit(node: JsonNode) {
        rows += JsonRow.Value(node)
        if (!node.isContainer || !isExpanded(node.path)) return
        val children = node.children()
        val shown = if (showsAll(node.path)) children else children.take(CHILDREN_LIMIT)
        shown.forEach(::visit)
        if (shown.size < children.size) {
            rows += JsonRow.More(node.path, children.size - shown.size, node.depth + 1)
        }
    }

    visit(root)
    return rows
}

/** Indent per level — enough to read as nesting, small enough that depth 6 still fits a phone. */
private val IndentStep = 12.dp

@Composable
private fun JsonNodeRow(
    node: JsonNode,
    expanded: Boolean,
    copied: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The whole row opens the branch; a scalar has nothing to open, so it stays inert and
            // its copy button is the only thing that reacts.
            .then(if (node.isContainer) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(start = 8.dp + IndentStep * node.depth, end = 4.dp)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (node.isContainer) {
            if (expanded) DownIcon(AppTheme.Muted, size = 14.dp) else ForwardIcon(AppTheme.Muted, size = 14.dp)
        } else {
            Spacer(Modifier.width(14.dp))
        }
        Spacer(Modifier.width(6.dp))
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            node.label?.let { label ->
                Text(
                    text = label,
                    style = AppTheme.Label,
                    color = AppTheme.Foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = ": ", style = AppTheme.Label, color = AppTheme.Muted)
            }
            // An open container has its children right below it, so it says only what it is and how
            // much of it there is; folded, it also carries a preview of the value itself.
            val trailing = if (node.isContainer && expanded) {
                node.element.countLabel().orEmpty()
            } else {
                listOfNotNull(node.element.countLabel(), node.element.preview()).joinToString(" · ")
            }
            Text(
                text = trailing,
                style = AppTheme.Label.copy(fontFamily = if (node.isContainer) FontFamily.Default else FontFamily.Monospace),
                color = valueColor(node.element),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        CopyButton(copied = copied, onCopy = onCopy)
    }
}

/** The row a cut-off container ends with — tapping it drops the cap for that container. */
@Composable
private fun MoreRow(row: JsonRow.More, onShowAll: () -> Unit) {
    Text(
        text = "…показать ещё ${row.hidden}",
        style = AppTheme.Label,
        color = AppTheme.Muted,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onShowAll)
            .padding(start = 28.dp + IndentStep * row.depth, end = 12.dp)
            .padding(vertical = 6.dp),
    )
}

/**
 * The per-row copy affordance, which turns into a checkmark for a moment after a tap — on a phone
 * the clipboard gives no other sign that anything happened.
 */
@Composable
private fun CopyButton(copied: Boolean, onCopy: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onCopy),
        contentAlignment = Alignment.Center,
    ) {
        if (copied) CheckIcon(AppTheme.Foreground, size = 16.dp) else CopyIcon(AppTheme.Muted, size = 16.dp)
    }
}

/** Scalars are tinted by type — the one hint that a `"1"` in a payload is not a `1`. */
private fun valueColor(element: JsonElement): Color = when {
    element is JsonNull -> AppTheme.Disabled
    element is JsonPrimitive && element.isString -> AppTheme.Foreground
    element is JsonPrimitive -> AppTheme.Primary
    else -> AppTheme.Muted
}
