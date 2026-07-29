package com.example.app.data

/** Everything the app needs after a successful login: where the server is, the token, and who. */
data class Session(
    val serverUrl: String,
    val token: String,
    val user: UserDto,
)
