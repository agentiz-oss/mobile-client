package com.example.app.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.example.app.BuildInfo
import com.example.app.theme.AppTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** One row of the slide-in menu. [danger] marks the destructive entry, i.e. signing out. */
data class MenuEntry(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val danger: Boolean = false,
)

/** How far in from the left edge a drag may start while the drawer is closed. */
private val EdgeSwipeWidth = 24.dp

/**
 * A fixed width, not a measured one: the panel is not composed while the drawer is closed, so the
 * first frame of an edge swipe needs a travel distance that already exists.
 */
private val DrawerWidth = 280.dp

/** Fling speed, in px/s, past which the drawer opens or closes regardless of how far it travelled. */
private const val FlingVelocity = 400f

@Composable
private fun rememberDrawerState(): DrawerState {
    val scope = rememberCoroutineScope()
    // Converted here rather than measured from the panel: the drawer is not composed while closed,
    // so the very first frame of an edge swipe needs a width that already exists.
    val widthPx = with(LocalDensity.current) { DrawerWidth.toPx() }
    // Seeded at construction so the width is right on the first frame — a burger tap that lands
    // before any effect has run must still animate the full distance.
    val state = remember(scope) { DrawerState(scope, widthPx) }
    // Only a later *change* is an effect: writing state straight from composition would mutate a
    // snapshot that may yet be discarded.
    SideEffect { state.onWidthChanged(widthPx) }
    return state
}

/**
 * The drawer's position, in pixels of horizontal travel from fully-closed (0f) to fully-open
 * ([width]).
 *
 * Offset is the single source of truth for both the panel and the scrim: dragging writes to it
 * directly so the panel tracks the finger 1:1, and the open/close animations write to the same
 * value, so a gesture can interrupt an animation mid-flight without the two fighting over the
 * panel's position.
 */
private class DrawerState(
    private val scope: kotlinx.coroutines.CoroutineScope,
    initialWidth: Float,
) {
    /**
     * Plain state, not an `Animatable`: a drag has to write the new position on the very frame the
     * pointer event arrives. `Animatable.snapTo` is suspending, so routing finger deltas through it
     * would defer each one to a coroutine, and the panel would trail the finger by a frame.
     */
    var offsetPx by mutableFloatStateOf(0f)
        private set

    /** Panel width in px — the distance a full open or close travels. */
    var width by mutableFloatStateOf(initialWidth)
        private set

    /** 0f closed, 1f fully open. Drives the scrim alpha and who owns which gesture. */
    val progress: Float get() = if (width <= 0f) 0f else (offsetPx / width).coerceIn(0f, 1f)

    /** The running open/close animation, cancelled the moment a finger touches the panel. */
    private var settleJob: Job? = null

    /**
     * The pixel width can change under the drawer when the window moves to a display of a different
     * density. Rescaling by the old-to-new ratio keeps a drawer that was open *stay* open across
     * that change instead of jumping to a stale pixel position.
     */
    fun onWidthChanged(newWidth: Float) {
        if (newWidth <= 0f || newWidth == width) return
        val previous = width
        if (previous > 0f) offsetPx = offsetPx / previous * newWidth
        width = newWidth
    }

    fun open() = animateTo(width)

    fun close() = animateTo(0f)

    private fun animateTo(target: Float) {
        settleJob?.cancel()
        // Width comes from density, so it is only ever zero in a degenerate composition; guarded
        // rather than assumed so an open() there cannot strand the panel at a nonsense offset.
        if (width <= 0f && target != 0f) return
        settleJob = scope.launch {
            animate(
                initialValue = offsetPx,
                targetValue = target,
                animationSpec = tween(if (target > offsetPx) 220 else 180),
            ) { value, _ -> offsetPx = value }
        }
    }

    /**
     * Takes over from whatever animation was running, so grabbing a drawer mid-slide hands it
     * straight to the finger instead of letting the two write competing positions.
     */
    fun onDragStarted() {
        settleJob?.cancel()
        settleJob = null
    }

    /** Moves the panel by one finger delta, clamped so it can never overshoot either end. */
    fun drag(delta: Float) {
        if (width <= 0f) return
        offsetPx = (offsetPx + delta).coerceIn(0f, width)
    }

    /**
     * Decides where the panel lands when the finger lifts: a decisive flick wins outright,
     * otherwise it settles to whichever end it is nearer.
     */
    fun settle(velocity: Float) {
        when {
            velocity > FlingVelocity -> open()
            velocity < -FlingVelocity -> close()
            progress > 0.5f -> open()
            else -> close()
        }
    }
}

/**
 * The frame every signed-in screen sits in: a fixed top bar with a burger on the left, and the
 * screen's own content below it. Navigation and signing out live in the drawer the burger opens,
 * so no screen has to spend its header on them.
 *
 * The bar and the drawer are pinned; only [content] scrolls, which is what keeps the burger
 * reachable from anywhere in a long task log.
 */
@Composable
fun AppScaffold(
    title: String,
    subtitle: String? = null,
    menu: List<MenuEntry>,
    onBack: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val drawer = rememberDrawerState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.Background)
            // Applied to the frame rather than to each screen so content clears the status bar
            // and the home indicator on every target exactly once.
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                title = title,
                subtitle = subtitle,
                onBack = onBack,
                onOpenMenu = { drawer.open() },
            )
            // weight, not fillMaxSize: the content takes the height the bar leaves rather than
            // the whole frame, which is what keeps a scrolling list from running under the bar.
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) { content() }
        }

        // A strip along the left edge that opens the drawer by drag. It sits over the content
        // rather than wrapping it, so a horizontally scrolling child anywhere else on the screen
        // keeps its own gestures — only the edge belongs to the drawer.
        EdgeSwipeCatcher(drawer = drawer)

        // The scrim and the drawer are siblings of the column, drawn after it, so they cover the
        // bar as well as the content — a half-covered top bar would look like a rendering bug.
        Scrim(drawer = drawer)
        MenuDrawer(drawer = drawer, entries = menu)
    }
}

/**
 * The left-edge grab area that opens the drawer by drag.
 *
 * It stays composed for the whole gesture and only stops *listening* once the panel is fully out —
 * removing the modifier the moment the offset left zero would cancel the very drag that was opening
 * it. Past that point the scrim and the panel own the gesture, and a catcher still live underneath
 * would steal the drag meant to push the panel back.
 */
@Composable
private fun BoxScope.EdgeSwipeCatcher(drawer: DrawerState) {
    Box(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .fillMaxHeight()
            .width(EdgeSwipeWidth)
            .draggable(
                orientation = Orientation.Horizontal,
                enabled = drawer.progress < 1f,
                state = rememberDraggableState { delta -> drawer.drag(delta) },
                onDragStarted = { drawer.onDragStarted() },
                onDragStopped = { velocity -> drawer.settle(velocity) },
            ),
    )
}

@Composable
private fun TopBar(
    title: String,
    subtitle: String?,
    onBack: (() -> Unit)?,
    onOpenMenu: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppTheme.Background)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onOpenMenu,
                label = "Открыть меню",
            ) { tint -> BurgerIcon(tint) }

            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            ) {
                Text(
                    text = title,
                    style = AppTheme.Subtitle,
                    color = AppTheme.Foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = AppTheme.Label,
                        color = AppTheme.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Back is the screen's own affordance, not the menu's, so it stays on the bar — but
            // only where there is somewhere to go back to.
            if (onBack != null) {
                IconButton(onClick = onBack, label = "Назад") { tint ->
                    BackIcon(tint)
                }
            }
        }
        Divider()
    }
}

/**
 * The panel itself. It is always composed and always laid out at its full width, then pushed off
 * the left edge by [DrawerState.offsetPx] — that is what lets a drag move it a pixel at a time
 * instead of playing a canned enter animation.
 */
@Composable
private fun BoxScope.MenuDrawer(drawer: DrawerState, entries: List<MenuEntry>) {
    // A fully-closed drawer is not on screen, so it is not composed: its rows would otherwise be
    // read out by a screen reader and matched by `onNodeWithText` while nobody can see them. The
    // panel's width is a constant rather than a measurement, so the drag can still position it on
    // the very first frame of a swipe, before this content has ever been laid out.
    if (drawer.progress <= 0f) return

    Column(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .fillMaxHeight()
            // A drawer, not a page: wide enough for the longest entry, but never the whole
            // window, so the dimmed screen behind it stays visible to tap back to.
            .width(DrawerWidth)
            // Parked one full width to the left when closed; every intermediate position is the
            // finger's. Read inside the lambda so a drag repositions the panel in the layout phase
            // alone, without recomposing the menu on every frame.
            .offset { IntOffset((drawer.offsetPx - drawer.width).roundToInt(), 0) }
            .background(AppTheme.Background)
            // Dragging the panel itself pushes it back.
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta -> drawer.drag(delta) },
                onDragStarted = { drawer.onDragStarted() },
                onDragStopped = { velocity -> drawer.settle(velocity) },
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                // Its own scroll: a menu that grows past a short window must not clip its last
                // entry, which on this app is the one that signs you out.
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "Меню", style = AppTheme.Subtitle, color = AppTheme.Foreground)
                IconButton(onClick = { drawer.close() }, label = "Закрыть меню") { tint ->
                    CloseIcon(tint)
                }
            }
            Spacer(Modifier.height(4.dp))
            Divider()
            Spacer(Modifier.height(8.dp))

            entries.forEach { entry ->
                MenuRow(entry = entry, onDismiss = { drawer.close() })
            }
        }

        // Pinned to the bottom of the panel rather than to the end of the scrolling list: the
        // build stamp is a footer, and a footer that scrolls away is one you have to hunt for.
        Divider()
        BuildStamp()
    }
}

/** Which build this is — the first thing to ask for when a bug report comes in. */
@Composable
private fun BuildStamp() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            text = "Версия ${BuildInfo.VERSION}",
            style = AppTheme.Label,
            color = AppTheme.Muted,
        )
        Text(
            text = BuildInfo.COMMIT + if (BuildInfo.DIRTY) "*" else "",
            style = AppTheme.Label,
            color = AppTheme.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MenuRow(entry: MenuEntry, onDismiss: () -> Unit) {
    val color = when {
        !entry.enabled -> AppTheme.Disabled
        entry.danger -> AppTheme.Danger
        else -> AppTheme.Foreground
    }
    Text(
        text = entry.label,
        style = AppTheme.Body,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(AppTheme.Radius))
            .clickable(enabled = entry.enabled, role = Role.Button) {
                // Closing before acting: every entry either navigates or signs out, and a drawer
                // still standing over the new screen would have to be dismissed by hand.
                onDismiss()
                entry.onClick()
            }
            .padding(horizontal = 12.dp, vertical = 14.dp),
    )
}

/**
 * Dims and blocks the content behind the drawer; tapping it closes the menu, and dragging it
 * pushes the panel back so the gesture can be finished without the finger ever finding the panel.
 *
 * Its alpha is tied to the drawer's travel, so the dimming arrives and leaves with the finger
 * rather than on a timer of its own.
 */
@Composable
private fun BoxScope.Scrim(drawer: DrawerState) {
    val open = drawer.progress > 0f
    Box(
        modifier = Modifier
            .matchParentSize()
            .then(
                // Composed only once there is something to dim: a full-screen box that is
                // transparent but still clickable would eat every tap on the closed app.
                if (open) {
                    Modifier
                        .background(ScrimColor.copy(alpha = ScrimColor.alpha * drawer.progress))
                        // No ripple and no role: this is a dismissal surface, not a button, and
                        // it should not announce itself as one.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { drawer.close() },
                        )
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta -> drawer.drag(delta) },
                            onDragStarted = { drawer.onDragStarted() },
                            onDragStopped = { velocity -> drawer.settle(velocity) },
                        )
                } else {
                    Modifier
                },
            ),
    )
}

private val ScrimColor = Color(0x66000000)

/** A 44.dp round tap target — the platform minimum — wrapping a hand-drawn glyph. */
@Composable
private fun IconButton(
    onClick: () -> Unit,
    label: String,
    enabled: Boolean = true,
    icon: @Composable (Color) -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        icon(if (enabled) AppTheme.Foreground else AppTheme.Disabled)
    }
}

@Composable
private fun Divider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(AppTheme.Border))
}
