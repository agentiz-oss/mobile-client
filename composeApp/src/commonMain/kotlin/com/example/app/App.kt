package com.example.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.app.data.Session
import com.example.app.screens.LoginScreen
import com.example.app.screens.ProjectsScreen

/**
 * Root of the app: a single source of truth for whether someone is signed in. No session shows the
 * login screen; a session shows their projects. Logging out simply clears the session.
 */
@Composable
fun App() {
    var session by remember { mutableStateOf<Session?>(null) }

    val current = session
    if (current == null) {
        LoginScreen(onLoggedIn = { session = it })
    } else {
        ProjectsScreen(session = current, onLogout = { session = null })
    }
}
