package com.example.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.example.app.theme.AppTheme

/**
 * The block vocabulary of a card-built screen, after GitHub's mobile app: a light
 * [AppTheme.PageBackground] page carrying white rounded cards, each card a stack of rows split by
 * inset hairlines; a bold [SectionHeader] over each card; rows led by a coloured [IconTile] or a
 * status disc and closed by a chevron; metadata worn as [MetaChip]s; the headline numbers of a
 * detail page in a [FactGrid]. Screens compose these instead of drawing their own frames, which is
 * what keeps two lists on two screens looking like one application.
 */

/** The heading over one card block — "Мои задачи" over the card that lists them. */
@Composable
fun SectionHeader(text: String, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = text, style = AppTheme.Header, color = AppTheme.Foreground)
        trailing?.invoke(this)
    }
}

/**
 * One white card block on the grey page: the container every grouped list sits in. It only frames —
 * the rows inside separate themselves with [InsetDivider], because only the call site knows which
 * neighbours are rows of one list and which are sub-parts of one row.
 */
@Composable
fun GroupedCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.Radius))
            .border(1.dp, AppTheme.Border, RoundedCornerShape(AppTheme.Radius))
            .background(AppTheme.Background),
        content = content,
    )
}

/**
 * The hairline between two rows of a [GroupedCard], inset from the left so it reads as a seam
 * inside the card rather than a cut through it — GitHub aligns it with the text, past the tile.
 */
@Composable
fun InsetDivider(start: Dp = 16.dp) {
    Row(modifier = Modifier.fillMaxWidth().background(AppTheme.Background)) {
        Spacer(Modifier.width(start))
        Box(modifier = Modifier.weight(1f).height(1.dp).background(AppTheme.Border))
    }
}

/**
 * The coloured rounded square leading a navigation row, GitHub's "My Work" tile: the fill comes
 * from the `Tile*` palette and the glyph is drawn in white on top of it.
 */
@Composable
fun IconTile(
    color: Color,
    size: Dp = 30.dp,
    icon: @Composable (tint: Color) -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(AppTheme.TileRadius))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        icon(Color.White)
    }
}

/**
 * One row of a [GroupedCard]: leading tile or status disc, a title with an optional muted line
 * under it, and a trailing chevron whenever the row goes somewhere. [trailing] replaces the
 * chevron for rows that end in a value or a control instead.
 */
@Composable
fun ListRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = {
        if (onClick != null) ForwardIcon(AppTheme.Muted, size = 14.dp)
    },
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { base ->
                if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppTheme.Body,
                color = AppTheme.Foreground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = AppTheme.Footnote,
                    color = AppTheme.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * A capsule of metadata under a row's title — "5 мин 27 с" with a stopwatch, a date with a
 * calendar: the outlined pill GitHub hangs under every workflow run. Always muted; a chip is
 * background information by definition, and one that shouts stops the title being read first.
 */
@Composable
fun MetaChip(text: String, icon: (@Composable (tint: Color) -> Unit)? = null) {
    Row(
        modifier = Modifier
            .border(1.dp, AppTheme.Border, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        icon?.invoke(AppTheme.Muted)
        Text(text = text, style = AppTheme.Footnote, color = AppTheme.Muted)
    }
}

/** One cell of a [FactGrid]. [color] tints the value — a status fact wears its state colour. */
data class Fact(val label: String, val value: String, val color: Color? = null)

/**
 * The headline numbers of a detail page as equal columns split by hairlines — GitHub's
 * Status / Duration / Billable Time strip. Facts a screen has no value for are simply not passed;
 * the grid divides whatever arrives.
 */
@Composable
fun FactGrid(facts: List<Fact>) {
    if (facts.isEmpty()) return
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        facts.forEachIndexed { index, fact ->
            if (index > 0) {
                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(AppTheme.Border))
            }
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(text = fact.label, style = AppTheme.Footnote, color = AppTheme.Muted)
                Text(
                    text = fact.value,
                    style = AppTheme.Label,
                    color = fact.color ?: AppTheme.Foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The one mapping from a run's (or stage's) status vocabulary to the state disc it wears in a
 * list — GitHub's workflow list read onto Agentiz statuses. Verdicts are discs, in-flight states
 * are dots, and the single state that needs a person is the single use of [AppTheme.Accent].
 */
@Composable
fun StatusIcon(status: String, size: Dp = 18.dp) {
    when (status) {
        "succeeded", "applied", "approved" -> CheckCircleFillIcon(AppTheme.Success, size)
        "failed", "push_failed", "reset_failed" -> XCircleFillIcon(AppTheme.Danger, size)
        "cancelled", "skipped", "rejected" -> SkipCircleFillIcon(AppTheme.Disabled, size)
        "waiting_input", "waiting_review" -> DotFillIcon(AppTheme.Accent, size)
        "running", "continuing" -> DotFillIcon(AppTheme.Warning, size)
        else -> DotFillIcon(AppTheme.Disabled, size)
    }
}
