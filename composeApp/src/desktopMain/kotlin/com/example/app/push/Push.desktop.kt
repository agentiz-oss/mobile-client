package com.example.app.push

/** No push on the desktop build: the questions screen polls, which is what it did before any of this. */
actual fun ensurePushRegistration() = Unit

/** No badge on desktop, as there are no notifications either. */
actual fun setAppBadge(count: Int) = Unit
