package com.example.app.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGAffineTransformMakeTranslation
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKWebView

@Composable
@OptIn(ExperimentalForeignApi::class)
actual fun EmbeddedDashboard(url: String, modifier: Modifier) {
    val drawerProgress = LocalDrawerProgress.current
    val drawerOpen = drawerProgress > 0f
    UIKitView(
        modifier = modifier,
        factory = {
            WKWebView().apply {
                NSURL.URLWithString(url)?.let { loadRequest(NSURLRequest(it)) }
            }
        },
        update = { webView ->
            // WKWebView is a UIKit layer and therefore does not inherit Compose's graphicsLayer.
            // A dp maps to a UIKit point here, so shift it by the same logical drawer width.
            webView.transform = CGAffineTransformMakeTranslation(
                (DrawerWidth.value * drawerProgress).toDouble(),
                0.0,
            )
            webView.userInteractionEnabled = !drawerOpen
        },
    )
}
