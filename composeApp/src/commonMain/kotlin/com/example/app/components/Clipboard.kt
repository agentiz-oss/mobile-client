package com.example.app.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

/**
 * The app's one way to put text on the clipboard.
 *
 * Compose's newer `LocalClipboard` is suspend-only and builds its entry through a platform type,
 * which would mean an expect/actual per target for what is one line here; the older manager is
 * common code on all five and does the same thing. Kept behind this seam so the day that changes,
 * it changes once.
 */
@Composable
fun rememberClipboardWriter(): (String) -> Unit {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    return { text -> @Suppress("DEPRECATION") clipboard.setText(AnnotatedString(text)) }
}
