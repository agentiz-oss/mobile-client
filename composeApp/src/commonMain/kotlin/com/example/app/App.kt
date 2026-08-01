package com.example.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.app.data.ProjectDto
import com.example.app.data.Session
import com.example.app.screens.LoginScreen
import com.example.app.screens.ProjectsScreen
import com.example.app.screens.TaskDetailScreen
import com.example.app.screens.TasksScreen

/**
 * Where the user is once they are signed in. A sealed hierarchy rather than a string route: the
 * screens below need the project and task they were opened with, and carrying them in the
 * destination keeps each screen a pure function of where the user navigated.
 */
private sealed interface Destination {
    data object Projects : Destination
    data class Tasks(val project: ProjectDto) : Destination
    data class Task(val project: ProjectDto, val taskId: String) : Destination
}

/**
 * Root of the app: a single source of truth for whether someone is signed in, and where they are.
 * No session shows the login screen; a session shows their projects, and from there the tasks of a
 * project and one task's run result and discussion. Logging out clears session and destination.
 */
@Composable
fun App() {
    var session by remember { mutableStateOf<Session?>(null) }
    var destination by remember { mutableStateOf<Destination>(Destination.Projects) }

    val current = session
    if (current == null) {
        LoginScreen(onLoggedIn = {
            session = it
            destination = Destination.Projects
        })
        return
    }

    when (val where = destination) {
        is Destination.Projects -> ProjectsScreen(
            session = current,
            onOpenProject = { destination = Destination.Tasks(it) },
            onLogout = {
                session = null
                destination = Destination.Projects
            },
        )

        is Destination.Tasks -> TasksScreen(
            session = current,
            project = where.project,
            onOpenTask = { destination = Destination.Task(where.project, it.id) },
            onBack = { destination = Destination.Projects },
        )

        is Destination.Task -> TaskDetailScreen(
            session = current,
            taskId = where.taskId,
            onBack = { destination = Destination.Tasks(where.project) },
        )
    }
}
