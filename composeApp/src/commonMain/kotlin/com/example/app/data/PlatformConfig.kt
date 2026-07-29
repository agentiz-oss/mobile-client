package com.example.app.data

/**
 * Default origin of the Agentiz server for a fresh install, resolved per platform. The Android
 * emulator reaches the host machine at 10.0.2.2, everything else at localhost. A real deployment
 * overrides this from the login screen's "Server" field.
 */
expect fun platformDefaultBaseUrl(): String
