package com.example.app.push

/**
 * No push in the browser build. Web push would need a service worker and a VAPID key pair, which is
 * a third transport on the server for a target that is only used for trying the app out.
 */
actual fun ensurePushRegistration() = Unit
