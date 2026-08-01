package com.example.app.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.example.app.theme.AppTheme

/**
 * A labelled single-line input built on the foundation [BasicTextField] primitive and styled to
 * match the app's monochrome, rounded look. Kept intentionally minimal — a bordered box, a muted
 * placeholder and an optional password mask — so it works identically on every target. Password
 * fields get a trailing eye button that reveals the typed value.
 */
@Composable
fun AppTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isPassword: Boolean = false,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val masked = isPassword && !passwordVisible

    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = label, style = AppTheme.Label, color = AppTheme.Foreground)
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = AppTheme.Body.copy(color = AppTheme.Foreground),
            cursorBrush = SolidColor(AppTheme.Primary),
            visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
                imeAction = imeAction,
            ),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AppTheme.Border, RoundedCornerShape(AppTheme.Radius))
                        .background(AppTheme.Background, RoundedCornerShape(AppTheme.Radius))
                        // The trailing button carries its own padding, so it only needs enough
                        // room here to sit on the same optical margin as the text.
                        .padding(start = 16.dp, end = if (isPassword) 4.dp else 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        // The vertical padding lives on the text rather than the row so the
                        // button can use the field's full height as its touch target.
                        modifier = Modifier.weight(1f).padding(vertical = 14.dp),
                    ) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(text = placeholder, style = AppTheme.Body, color = AppTheme.Muted)
                        }
                        innerTextField()
                    }
                    if (isPassword) {
                        PasswordVisibilityToggle(
                            visible = passwordVisible,
                            enabled = enabled,
                            onToggle = { passwordVisible = !passwordVisible },
                        )
                    }
                }
            },
        )
    }
}

/**
 * The eye button shown at the end of a password field. Shows a plain eye while the value is
 * masked ("tap to reveal") and a struck-through one while it is readable ("tap to hide").
 */
@Composable
private fun PasswordVisibilityToggle(
    visible: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            // Clipped first so the hover and press highlight stays a disc inside the field
            // instead of a square running into its rounded border.
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onToggle)
            .semantics {
                contentDescription = if (visible) "Скрыть пароль" else "Показать пароль"
            },
        contentAlignment = Alignment.Center,
    ) {
        EyeIcon(
            crossedOut = visible,
            tint = when {
                !enabled -> AppTheme.Disabled
                visible -> AppTheme.Foreground
                else -> AppTheme.Muted
            },
        )
    }
}

/**
 * An eye glyph drawn by hand — the project pulls in no icon pack, and a couple of curves cost far
 * less than one. When [crossedOut] the diagonal is outlined in the field colour so it stays
 * readable where it crosses the eye.
 */
@Composable
private fun EyeIcon(
    crossedOut: Boolean,
    tint: Color,
) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val lineWidth = w * 0.09f
        val stroke = Stroke(width = lineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)

        val outline = Path().apply {
            moveTo(w * 0.05f, h * 0.5f)
            quadraticTo(w * 0.5f, h * 0.04f, w * 0.95f, h * 0.5f)
            quadraticTo(w * 0.5f, h * 0.96f, w * 0.05f, h * 0.5f)
            close()
        }
        drawPath(path = outline, color = tint, style = stroke)
        drawCircle(color = tint, radius = w * 0.16f, center = center, style = stroke)

        if (crossedOut) {
            val start = Offset(w * 0.14f, h * 0.86f)
            val end = Offset(w * 0.86f, h * 0.14f)
            drawLine(AppTheme.Background, start, end, strokeWidth = lineWidth * 2.6f, cap = StrokeCap.Round)
            drawLine(tint, start, end, strokeWidth = lineWidth, cap = StrokeCap.Round)
        }
    }
}
