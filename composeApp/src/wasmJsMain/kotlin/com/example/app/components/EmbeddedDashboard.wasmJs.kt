package com.example.app.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier

/**
 * Compose for web is canvas-based here, so an actual HTML iframe is layered over the sheet's
 * browser area. It is removed as soon as the sheet leaves composition.
 */
@Composable
actual fun EmbeddedDashboard(url: String, modifier: Modifier) {
    DisposableEffect(url) {
        appendDashboardFrame(url)
        onDispose {
            removeDashboardFrame()
        }
    }
}

private fun appendDashboardFrame(url: String) {
    js("""
        (() => {
            const frame = document.createElement('iframe');
            frame.id = 'agentiz-dashboard-frame';
            frame.src = url;
            frame.title = 'Диалог с агентом';
            frame.style.cssText = 'position:fixed;left:0;bottom:0;width:100vw;height:calc(86vh - 106px);border:0;background:white;z-index:2';
            document.body.appendChild(frame);
        })()
    """)
}

private fun removeDashboardFrame() {
    js("document.getElementById('agentiz-dashboard-frame')?.remove()")
}
