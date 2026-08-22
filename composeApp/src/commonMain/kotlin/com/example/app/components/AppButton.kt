package com.example.app.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composeunstyled.Button
import com.composeunstyled.Text
import com.example.app.theme.AppTheme

/**
 * Visual weight of a button. Ported from RikkaUI's `Button`
 * (`components/ui/button/Button.kt`) by the copy-and-recolour route
 * `.ai-notes/mobile-ui-kits-evaluation.md` recommends over taking the dependency: the
 * variant/size vocabulary is theirs, the colours are [AppTheme]'s, and hover/press morphing is
 * dropped — a phone has no hover, and the shape interpolation was the bulk of the original.
 *
 * [Destructive] is a *tint*, not a filled red button: a list row's title has to stay the loudest
 * thing in it, which is the same reason GitHub tints «Review now» instead of filling it.
 */
enum class ButtonVariant { Default, Outline, Secondary, Ghost, Destructive, Accent }

/** [Lg] is the page-sized button this file has always had; [Sm] is the one that fits in a row. */
enum class ButtonSize { Sm, Default, Lg }

/**
 * A button built on the Compose Unstyled [Button] primitive and styled to match the app's
 * monochrome, rounded look. The defaults are the original page-sized primary button, so every
 * existing call site keeps its appearance.
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: ButtonVariant = ButtonVariant.Default,
    size: ButtonSize = ButtonSize.Lg,
) {
    val background = when (variant) {
        ButtonVariant.Default -> AppTheme.Primary
        ButtonVariant.Secondary -> AppTheme.Surface
        ButtonVariant.Outline, ButtonVariant.Ghost -> AppTheme.Background
        ButtonVariant.Destructive -> AppTheme.DangerSubtle
        ButtonVariant.Accent -> AppTheme.AccentSubtle
    }
    val foreground = when (variant) {
        ButtonVariant.Default -> AppTheme.PrimaryForeground
        ButtonVariant.Secondary, ButtonVariant.Outline, ButtonVariant.Ghost -> AppTheme.Foreground
        ButtonVariant.Destructive -> AppTheme.Danger
        ButtonVariant.Accent -> AppTheme.Accent
    }
    val border = when (variant) {
        ButtonVariant.Outline, ButtonVariant.Secondary -> AppTheme.Border
        else -> Color.Transparent
    }
    val padding = when (size) {
        ButtonSize.Sm -> PaddingValues(horizontal = 12.dp, vertical = 7.dp)
        ButtonSize.Default -> PaddingValues(horizontal = 20.dp, vertical = 12.dp)
        ButtonSize.Lg -> PaddingValues(horizontal = 40.dp, vertical = 18.dp)
    }

    Button(
        // Compose Unstyled's Button has no disabled state of its own, so gate the click here and
        // dim the surface to signal it.
        onClick = { if (enabled) onClick() },
        modifier = modifier,
        shape = RoundedCornerShape(if (size == ButtonSize.Sm) 8.dp else AppTheme.Radius),
        backgroundColor = if (enabled) background else AppTheme.Disabled,
        contentColor = if (enabled) foreground else AppTheme.PrimaryForeground,
        borderColor = if (enabled) border else Color.Transparent,
        borderWidth = if (border == Color.Transparent) 0.dp else 1.dp,
        contentPadding = padding,
    ) {
        Text(
            text = text,
            style = if (size == ButtonSize.Sm) AppTheme.Label else AppTheme.ButtonLabel,
        )
    }
}
