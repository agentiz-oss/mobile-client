package com.example.app.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.Button
import com.composeunstyled.Text
import com.example.app.theme.AppTheme

/**
 * A large primary button built on the Compose Unstyled [Button] primitive and
 * styled to match the app's monochrome, rounded, SaaS-style look.
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(AppTheme.Radius),
        backgroundColor = AppTheme.Primary,
        contentColor = AppTheme.PrimaryForeground,
        contentPadding = PaddingValues(horizontal = 40.dp, vertical = 18.dp),
    ) {
        Text(
            text = text,
            style = AppTheme.ButtonLabel,
        )
    }
}
