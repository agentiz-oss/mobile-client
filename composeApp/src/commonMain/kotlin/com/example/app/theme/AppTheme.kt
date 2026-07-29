package com.example.app.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Minimal, monochrome design tokens inspired by shadcn/ui, Radix and Tailwind:
 * a white surface, near-black text and a single black primary accent.
 */
@Immutable
object AppTheme {
    val Background: Color = Color(0xFFFFFFFF)
    val Foreground: Color = Color(0xFF09090B)
    val Primary: Color = Color(0xFF18181B)
    val PrimaryForeground: Color = Color(0xFFFAFAFA)

    val Radius: Dp = 12.dp

    val Title: TextStyle = TextStyle(
        fontSize = 34.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.SemiBold,
    )

    val ButtonLabel: TextStyle = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium,
    )
}
