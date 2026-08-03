package com.example.app.components

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun EmbeddedDashboard(url: String, modifier: Modifier) {
    val drawerProgress = LocalDrawerProgress.current
    val drawerOpen = drawerProgress > 0f
    val drawerOffset = with(LocalDensity.current) { DrawerWidth.toPx() * drawerProgress }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                loadUrl(url)
            }
        },
        update = { webView ->
            // AndroidView has its own render node and does not inherit the sheet's graphicsLayer.
            // Move that node with the card instead of hiding the dashboard.
            webView.translationX = drawerOffset
            webView.isEnabled = !drawerOpen
        },
    )
}
