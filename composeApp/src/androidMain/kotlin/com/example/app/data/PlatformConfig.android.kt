package com.example.app.data

// The Android emulator forwards 10.0.2.2 to the host loopback; a device on the LAN needs the
// server's real address, entered on the login screen.
actual fun platformDefaultBaseUrl(): String = "http://10.0.2.2:17280"
