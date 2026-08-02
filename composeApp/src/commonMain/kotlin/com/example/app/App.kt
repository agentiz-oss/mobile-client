package com.example.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.app.components.MenuEntry
import com.example.app.data.ProjectDto
import com.example.app.data.Session
import com.example.app.data.clearSession
import com.example.app.data.loadSession
import com.example.app.data.saveSession
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
 * project and one task's run result and discussion.
 *
 * The session is read back from storage as the app starts, so a relaunch lands where the user left
 * off rather than on the login screen; signing out is what clears it again.
 */
@Composable
fun App() {
    // Restored once, at the first composition, rather than in an effect: doing it here means the
    // very first frame is already the right screen, with no login flash before it.
    var session by remember { mutableStateOf(loadSession()) }
    var destination by remember { mutableStateOf<Destination>(Destination.Projects) }

    fun logout() {
        clearSession()
        session = null
        destination = Destination.Projects
    }

    val current = session
    if (current == null) {
        LoginScreen(onLoggedIn = {
            saveSession(it)
            session = it
            destination = Destination.Projects
        })
        return
    }

    /**
     * The drawer's contents. Built here rather than inside each screen because the menu is about
     * the session, not the page: it is the same list everywhere, with the entry for wherever you
     * already are disabled.
     */
    val menu = buildList {
        add(
            MenuEntry(
                label = "Проекты",
                onClick = { destination = Destination.Projects },
                enabled = destination !is Destination.Projects,
            ),
        )
        // Only meaningful once a project has been opened — from the project list there is no
        // "current project" to go back up to.
        val project = when (val where = destination) {
            is Destination.Tasks -> where.project
            is Destination.Task -> where.project
            is Destination.Projects -> null
        }
        if (project != null) {
            add(
                MenuEntry(
                    label = "Задачи: ${project.name}",
                    onClick = { destination = Destination.Tasks(project) },
                    enabled = destination !is Destination.Tasks,
                ),
            )
        }
        add(MenuEntry(label = "Выйти", onClick = ::logout, danger = true))
    }

    val userLabel = current.user.fullName?.takeIf { it.isNotBlank() } ?: current.user.login

    when (val where = destination) {
        is Destination.Projects -> ProjectsScreen(
            session = current,
            menu = menu,
            userLabel = userLabel,
            onOpenProject = { destination = Destination.Tasks(it) },
        )

        is Destination.Tasks -> TasksScreen(
            session = current,
            project = where.project,
            menu = menu,
            onOpenTask = { destination = Destination.Task(where.project, it.id) },
            onBack = { destination = Destination.Projects },
        )

        is Destination.Task -> TaskDetailScreen(
            session = current,
            taskId = where.taskId,
            menu = menu,
            onBack = { destination = Destination.Tasks(where.project) },
        )
    }
}
