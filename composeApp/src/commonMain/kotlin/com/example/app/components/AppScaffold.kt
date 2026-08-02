package com.example.app.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.example.app.theme.AppTheme

/** One row of the slide-in menu. [danger] marks the destructive entry, i.e. signing out. */
data class MenuEntry(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val danger: Boolean = false,
)

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
    var menuOpen by remember { mutableStateOf(false) }

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
                onOpenMenu = { menuOpen = true },
            )
            // weight, not fillMaxSize: the content takes the height the bar leaves rather than
            // the whole frame, which is what keeps a scrolling list from running under the bar.
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) { content() }
        }

        // The scrim and the drawer are siblings of the column, drawn after it, so they cover the
        // bar as well as the content — a half-covered top bar would look like a rendering bug.
        Scrim(visible = menuOpen, onDismiss = { menuOpen = false })
        MenuDrawer(
            visible = menuOpen,
            entries = menu,
            onDismiss = { menuOpen = false },
        )
    }
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
 * The panel itself. It slides in from the left under the burger that opened it, and is capped at
 * 320.dp so it stays a drawer on a desktop window instead of becoming a full-width page.
 */
@Composable
private fun BoxScope.MenuDrawer(
    visible: Boolean,
    entries: List<MenuEntry>,
    onDismiss: () -> Unit,
) {
    // Aligned rather than filled: the drawer is a left-hand panel, and an AnimatedVisibility that
    // filled its parent would slide a full-width sheet across the screen instead.
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxHeight().align(Alignment.CenterStart),
        enter = slideInHorizontally(animationSpec = tween(220)) { -it },
        exit = slideOutHorizontally(animationSpec = tween(180)) { -it },
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                // A drawer, not a page: wide enough for the longest entry, but never the whole
                // window, so the dimmed screen behind it stays visible to tap back to.
                .widthIn(min = 240.dp, max = 300.dp)
                .background(AppTheme.Background)
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
                IconButton(onClick = onDismiss, label = "Закрыть меню") { tint ->
                    CloseIcon(tint)
                }
            }
            Spacer(Modifier.height(4.dp))
            Divider()
            Spacer(Modifier.height(8.dp))

            entries.forEach { entry ->
                MenuRow(entry = entry, onDismiss = onDismiss)
            }
        }
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

/** Dims and blocks the content behind the drawer; tapping it closes the menu. */
@Composable
private fun BoxScope.Scrim(visible: Boolean, onDismiss: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.matchParentSize(),
        enter = fadeIn(animationSpec = tween(220)),
        exit = fadeOut(animationSpec = tween(180)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ScrimColor)
                // No ripple and no role: this is a dismissal surface, not a button, and it should
                // not announce itself as one.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
    }
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
