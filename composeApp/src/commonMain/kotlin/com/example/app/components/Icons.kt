package com.example.app.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/**
 * The handful of glyphs the chrome needs, drawn by hand. The project pulls in no icon pack, and at
 * three shapes a few line calls cost far less than one — the same trade the eye icon already makes.
 */

/** The burger that opens the menu: three evenly spaced rules. */
@Composable
fun BurgerIcon(tint: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.1f
        listOf(0.24f, 0.5f, 0.76f).forEach { y ->
            drawLine(
                color = tint,
                start = Offset(w * 0.1f, h * y),
                end = Offset(w * 0.9f, h * y),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** An X, for dismissing the menu from inside it. */
@Composable
fun CloseIcon(tint: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.11f
        drawLine(tint, Offset(w * 0.16f, h * 0.16f), Offset(w * 0.84f, h * 0.84f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.84f, h * 0.16f), Offset(w * 0.16f, h * 0.84f), stroke, StrokeCap.Round)
    }
}

/** A left-pointing chevron for the back affordance. */
@Composable
fun BackIcon(tint: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.12f
        drawLine(tint, Offset(w * 0.62f, h * 0.12f), Offset(w * 0.28f, h * 0.5f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.28f, h * 0.5f), Offset(w * 0.62f, h * 0.88f), stroke, StrokeCap.Round)
    }
}
