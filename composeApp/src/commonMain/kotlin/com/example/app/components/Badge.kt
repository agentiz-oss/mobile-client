package com.example.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.example.app.theme.AppTheme

/**
 * A small status label — the pill a list row wears to say what kind of thing it is.
 *
 * Ported from RikkaUI's `Badge` (`components/ui/badge/Badge.kt`, Apache-2.0 per its POM) by the
 * copy-and-recolour route `.ai-notes/mobile-ui-kits-evaluation.md` recommends over a dependency:
 * the variant/size vocabulary is theirs, the colours are [AppTheme]'s, and the entrance animations
 * are dropped — nothing here appears out of nowhere, and a chip that pulses on every list refresh
 * is noise.
 */
enum class BadgeVariant {
    /** Solid, the app's chrome colour — the neutral "this is a thing" chip. */
    Default,

    /** Muted fill: present, but not competing with the row's title. */
    Secondary,

    /** Bordered and transparent. */
    Outline,

    /** Red: something is broken. */
    Destructive,

    /** The blue reserved for "this needs a person". */
    Accent,
}

enum class BadgeSize { Sm, Default }

@Composable
fun Badge(
    text: String,
    modifier: Modifier = Modifier,
    variant: BadgeVariant = BadgeVariant.Default,
    size: BadgeSize = BadgeSize.Default,
) {
    val shape = RoundedCornerShape(999.dp)
    val background = when (variant) {
        BadgeVariant.Default -> AppTheme.Primary
        BadgeVariant.Secondary -> AppTheme.Surface
        BadgeVariant.Outline -> Color.Transparent
        BadgeVariant.Destructive -> AppTheme.DangerSubtle
        BadgeVariant.Accent -> AppTheme.AccentSubtle
    }
    val foreground = when (variant) {
        BadgeVariant.Default -> AppTheme.PrimaryForeground
        BadgeVariant.Secondary -> AppTheme.Muted
        BadgeVariant.Outline -> AppTheme.Foreground
        BadgeVariant.Destructive -> AppTheme.Danger
        BadgeVariant.Accent -> AppTheme.Accent
    }
    val border = when (variant) {
        BadgeVariant.Outline, BadgeVariant.Secondary -> AppTheme.Border
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .clip(shape)
            .then(if (background != Color.Transparent) Modifier.background(background, shape) else Modifier)
            .then(if (border != Color.Transparent) Modifier.border(1.dp, border, shape) else Modifier)
            .padding(
                horizontal = if (size == BadgeSize.Sm) 6.dp else 8.dp,
                vertical = if (size == BadgeSize.Sm) 1.dp else 2.dp,
            ),
    ) {
        Text(text = text, style = AppTheme.Footnote, color = foreground)
    }
}
