package com.example.app.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKWebView

@Composable
actual fun EmbeddedDashboard(url: String, modifier: Modifier) {
    UIKitView(
        modifier = modifier,
        factory = { WKWebView() },
        update = { webView ->
            NSURL.URLWithString(url)?.let { webView.loadRequest(NSURLRequest(it)) }
        },
    )
}
