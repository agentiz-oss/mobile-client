package com.example.app.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.example.app.platform.hapticActionComplete
import com.example.app.theme.AppTheme
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One thing a row can be swiped to do: the caption and colour of the panel that appears behind it,
 * and what happens when the swipe is carried far enough.
 */
data class SwipeAction(
    val label: String,
    val background: Color,
    val foreground: Color = Color.White,
    val icon: (@Composable (Color) -> Unit)? = null,
    val onAction: () -> Unit,
)

/** Past this much travel the swipe counts as done; below it the row springs back untouched. */
private val CommitDistance = 96.dp

/** And this is as far as the row will move, so the panel behind it stays a hint, not a screen. */
private val MaxTravel = 132.dp

/**
 * A list row that can be dragged aside to reveal one action on each side.
 *
 * The gesture is the point: the two things a reader may want from an inbox row that are *not* its
 * decision — «прочитал и заниматься не буду» and «перестань мне про это писать» — are both
 * secondary to the row's own buttons, and both would crowd the card if they were buttons too.
 * Behind the row instead: drag right for [start], drag left for [end].
 *
 * Nothing here is the only way to reach an action. A swipe is invisible until it is tried, so
 * whatever is offered this way also exists as a button or a tappable line inside the row — this is
 * the shortcut, not the door.
 */
@Composable
fun SwipeableRow(
    start: SwipeAction? = null,
    end: SwipeAction? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (start == null && end == null) {
        Box(modifier = modifier) { content() }
        return
    }

    val density = LocalDensity.current
    val commit = with(density) { CommitDistance.toPx() }
    val maxTravel = with(density) { MaxTravel.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    // Read at the end of the gesture, not when the modifier was built: the row is rebuilt on every
    // poll of the list, and a captured stale lambda would act on the row as it was five seconds ago.
    val current = rememberUpdatedState(start to end)

    val revealed = offset.value
    val armed = abs(revealed) >= commit

    Box(modifier = modifier.fillMaxWidth()) {
        // Only the side actually being dragged is drawn: two panels sharing the box would both show
        // through a half-open row and read as two buttons nobody pressed.
        when {
            revealed > 0f && start != null -> SwipePanel(start, Alignment.CenterStart, armed)
            revealed < 0f && end != null -> SwipePanel(end, Alignment.CenterEnd, armed)
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            val (left, right) = current.value
                            val next = offset.value + delta
                            // Only travel towards a side that has something to offer.
                            val clamped = when {
                                next > 0f && left == null -> 0f
                                next < 0f && right == null -> 0f
                                else -> next.coerceIn(-maxTravel, maxTravel)
                            }
                            offset.snapTo(clamped)
                        }
                    },
                    onDragStopped = {
                        val travelled = offset.value
                        offset.animateTo(0f)
                        val (left, right) = current.value
                        val action = when {
                            travelled >= commit -> left
                            travelled <= -commit -> right
                            else -> null
                        }
                        if (action != null) {
                            hapticActionComplete()
                            action.onAction()
                        }
                    },
                ),
        ) {
            content()
        }
    }
}

/** What shows behind a half-open row: the caption, in colour once the swipe would commit. */
@Composable
private fun BoxScope.SwipePanel(action: SwipeAction, alignment: Alignment, armed: Boolean) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(if (armed) action.background else AppTheme.Surface),
        contentAlignment = alignment,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (alignment == Alignment.CenterStart) Arrangement.Start else Arrangement.End,
        ) {
            val tint = if (armed) action.foreground else AppTheme.Muted
            action.icon?.let { glyph ->
                glyph(tint)
                Spacer(Modifier.width(8.dp))
            }
            Text(text = action.label, style = AppTheme.Label, color = tint)
        }
    }
}
