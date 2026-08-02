package com.example.app.platform

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyleLight

/**
 * Taptic Engine impact. Light style is the one iOS itself uses to acknowledge a completed action —
 * medium and heavy read as warnings.
 *
 * The generator is created per call rather than held in a property: `prepare()` immediately before
 * the impact is what keeps the engine warm and the latency low, and a long-lived instance would
 * have to be pinned to the main thread anyway. UIKit ignores the whole thing when the user has
 * disabled system haptics or the device has no Taptic Engine.
 */
actual fun hapticActionComplete() {
    val generator = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyleLight)
    generator.prepare()
    generator.impactOccurred()
}
