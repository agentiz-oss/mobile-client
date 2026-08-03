package com.example.app.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import java.net.URL
import javax.swing.JEditorPane

@Composable
actual fun EmbeddedDashboard(url: String, modifier: Modifier) {
    SwingPanel(
        modifier = modifier,
        factory = {
            JEditorPane().apply {
                isEditable = false
                contentType = "text/html"
                setPage(URL(url))
            }
        },
    )
}
