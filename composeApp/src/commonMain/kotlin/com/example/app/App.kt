package com.example.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.example.app.components.MenuEntry
import com.example.app.data.AgentizApi
import com.example.app.data.ProjectDto
import com.example.app.data.Session
import com.example.app.data.clearSession
import com.example.app.data.loadSession
import com.example.app.data.saveSession
import com.example.app.data.ActivitySummaryDto
import com.example.app.screens.ActivitiesScreen
import com.example.app.screens.InteractionsScreen
import com.example.app.screens.LoginScreen
import com.example.app.screens.AgentDashboardScreen
import com.example.app.screens.ProfileScreen
import com.example.app.screens.ProjectsScreen
import com.example.app.screens.RunDetailScreen
import com.example.app.screens.RunsScreen
import com.example.app.screens.SettingsScreen
import com.example.app.screens.TaskDetailScreen
import com.example.app.screens.TasksScreen
import com.example.app.screens.ViewerTime
import com.example.app.screens.WorkersScreen
import com.example.app.push.Push
import com.example.app.push.PushRoute
import com.example.app.push.ensurePushRegistration
import com.example.app.push.setAppBadge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How often the drawer's counters refresh. Cheap enough to run for the whole session. */
private const val BADGE_POLL_MS = 15_000L

/**
 * Where the user is once they are signed in. A sealed hierarchy rather than a string route: the
 * screens below need the project and task they were opened with, and carrying them in the
 * destination keeps each screen a pure function of where the user navigated.
 */
private sealed interface Destination {
    data object Projects : Destination
    data class Tasks(val project: ProjectDto) : Destination
    data class Task(val project: ProjectDto, val taskId: String) : Destination

    /**
     * One run's own page, opened from its card in the task's history — or from the run board, which
     * has no run number to hand and is not the task screen either, hence [from]: back returns to
     * wherever the run was opened, defaulting to its task. [runNumber] is only along for the title
     * — the run itself is looked up by [runId].
     */
    data class Run(
        val project: ProjectDto,
        val taskId: String,
        val runId: String,
        val runNumber: Int?,
        val from: Destination? = null,
    ) : Destination

    data class Agent(val from: Destination) : Destination

    /**
     * Every agent question waiting on the user, regardless of which project it came from.
     *
     * [focusInteractionId] is set when the screen was opened from a notification: that one question
     * is pulled to the top and opened, and it is fetched by id if it is no longer in the pending
     * list — a push tapped ten minutes late must still explain itself.
     */
    data class Interactions(val from: Destination, val focusInteractionId: String? = null) : Destination

    /**
     * The activity feed: everything that happened across the user's projects, with the actionable
     * part (questions, reviews, held diffs) pulled to the top.
     */
    data class Activities(val from: Destination) : Destination

    /** Every run in flight, regardless of which project or task it belongs to. */
    data class Runs(val from: Destination) : Destination

    /**
     * The machines the work runs on and the harness limits they run under. Installation-wide, like
     * [Runs] — a worker belongs to the deployment, not to a project — so it only carries where it
     * was opened from.
     */
    data class Workers(val from: Destination) : Destination

    /**
     * The two pages behind the drawer's footer icons. Each carries where it was opened from so
     * back returns there rather than dumping the user on the project list — they are reachable
     * from anywhere, so there is no one place "back" could otherwise mean.
     */
    data class Settings(val from: Destination) : Destination
    data class Profile(val from: Destination) : Destination
}

/**
 * The project a destination is "in", if any. Settings and Profile inherit it from wherever they
 * were opened, so the drawer keeps offering the project you were last looking at.
 *
 * Non-recursive by construction: neither wrapper is ever built around another, since both are only
 * ever opened from a content screen.
 */
private fun Destination.project(): ProjectDto? = when (this) {
    is Destination.Projects -> null
    is Destination.Tasks -> project
    is Destination.Task -> project
    is Destination.Run -> project
    is Destination.Agent -> from.project()
    is Destination.Interactions -> from.project()
    is Destination.Activities -> from.project()
    is Destination.Runs -> from.project()
    is Destination.Workers -> from.project()
    is Destination.Settings -> from.project()
    is Destination.Profile -> from.project()
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
    val scope = rememberCoroutineScope()

    fun logout() {
        // Tell the server to forget this phone *before* the session is dropped — afterwards there is
        // no token left to authenticate the call, and the device would keep receiving questions
        // belonging to a user who is no longer signed in here.
        val signedIn = session
        val pushToken = Push.currentToken()
        if (signedIn != null && pushToken != null) {
            scope.launch {
                val api = AgentizApi(signedIn.serverUrl)
                try {
                    runCatching { api.unregisterDevice(signedIn.token, pushToken) }
                } finally {
                    api.close()
                }
            }
        }
        clearSession()
        session = null
        destination = Destination.Projects
    }

    val current = session
    // Every timestamp the screens render shifts by the signed-in user's timezone offset; reset on
    // logout so the login screen era shows plain UTC rather than the previous user's zone.
    ViewerTime.utcOffsetMinutes = current?.user?.utcOffsetMinutes
    if (current == null) {
        LoginScreen(onLoggedIn = {
            saveSession(it)
            session = it
            destination = Destination.Projects
        })
        return
    }

    fun openAgent() {
        destination = Destination.Agent(destination)
    }

    // Push, in both directions. Asking for the token only once someone is signed in keeps the
    // permission prompt away from the login screen, where it would be a prompt about nothing.
    LaunchedEffect(current.token) { ensurePushRegistration() }

    // Refresh who we are on every (re)start: the stored session carries the timezone offset from
    // login day, and DST or a profile edit moves it. Failure is silent — the stale copy still works.
    LaunchedEffect(current.token, current.serverUrl) {
        runCatching { AgentizApi(current.serverUrl).me(current.token) }.onSuccess { fresh ->
            val updated = current.copy(user = fresh)
            if (updated != current) {
                saveSession(updated)
                session = updated
            }
        }
    }

    val registration by Push.registration.collectAsState()
    LaunchedEffect(registration, current.token, current.serverUrl) {
        val push = registration ?: return@LaunchedEffect
        val api = AgentizApi(current.serverUrl)
        try {
            // Failure is survivable and deliberately silent: without a registered token the user
            // simply gets no notifications, which is exactly how the app behaved before.
            runCatching {
                api.registerDevice(
                    token = current.token,
                    pushToken = push.token,
                    platform = push.platform,
                    appVersion = BuildInfo.label,
                )
            }
                .onFailure { println("[push] device registration failed: ${it.message}") }
        } finally {
            api.close()
        }
    }

    // A tapped notification. It can arrive at any moment, including before the first frame after a
    // cold start, so it is consumed here rather than by whichever screen happens to be open.
    val pendingRoute by Push.route.collectAsState()
    LaunchedEffect(pendingRoute) {
        val route = Push.consumeRoute() ?: return@LaunchedEffect
        val here = destination
        // Back has to lead somewhere the user recognises. Opening a second notification while
        // already on a pushed-to screen would otherwise make that screen its own parent.
        val from = when (here) {
            is Destination.Interactions -> here.from
            is Destination.Activities -> here.from
            else -> here
        }
        destination = when (route) {
            is PushRoute.Question -> Destination.Interactions(from = from, focusInteractionId = route.interactionId)
            is PushRoute.Activity -> {
                val taskId = route.taskId
                val runId = route.runId
                if (taskId != null && runId != null) {
                    // The event lives on a run's page — a review, a failed push, a finished run.
                    Destination.Run(
                        project = ProjectDto(id = route.projectId ?: "", name = route.projectName ?: "Проект"),
                        taskId = taskId,
                        runId = runId,
                        runNumber = null,
                        from = from,
                    )
                } else {
                    Destination.Activities(from = from)
                }
            }
        }
    }

    // One summary request feeds every ambient counter: the questions badge, the activities badge
    // and the app icon. Without it a parked run or a waiting review is invisible unless the user
    // already stands on the right screen.
    var summary by remember { mutableStateOf(ActivitySummaryDto()) }
    // Same idea for runs: without a count here, "что-то идёт прямо сейчас" is only discoverable by
    // opening the board, and a run that started elsewhere would never announce itself.
    var activeRuns by remember { mutableStateOf(0) }
    LaunchedEffect(current.serverUrl, current.token) {
        val api = AgentizApi(current.serverUrl)
        try {
            while (true) {
                summary = runCatching { api.activitySummary(current.token) }.getOrDefault(summary)
                // The icon says the same thing the drawer does: everything actionable, not only
                // questions. Owned by the app so acting on things clears it between two pushes.
                setAppBadge(summary.actionableCount)
                activeRuns = runCatching { api.runBoard(current.token).active.size }
                    .getOrDefault(activeRuns)
                delay(BADGE_POLL_MS)
            }
        } finally {
            api.close()
        }
    }
    val pendingInteractions = summary.interactions.size

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
        // "current project" to go back up to. Settings and Profile are not places a project is
        // open *in*, but they were opened from somewhere that might have had one, so the entry
        // follows the destination they came from.
        val project = destination.project()
        if (project != null) {
            add(
                MenuEntry(
                    label = "Задачи: ${project.name}",
                    onClick = { destination = Destination.Tasks(project) },
                    enabled = destination !is Destination.Tasks,
                ),
            )
        }
        add(
            MenuEntry(
                label = if (activeRuns > 0) "Запуски ($activeRuns)" else "Запуски",
                onClick = { destination = Destination.Runs(destination) },
                enabled = destination !is Destination.Runs,
            ),
        )
        add(
            MenuEntry(
                label = if (pendingInteractions > 0) "Вопросы ($pendingInteractions)" else "Вопросы",
                onClick = { destination = Destination.Interactions(destination) },
                enabled = destination !is Destination.Interactions,
            ),
        )
        add(
            MenuEntry(
                label = if (summary.unseen > 0) "Активности (${summary.unseen})" else "Активности",
                onClick = { destination = Destination.Activities(destination) },
                enabled = destination !is Destination.Activities,
            ),
        )
        add(
            MenuEntry(
                label = "Воркеры",
                onClick = { destination = Destination.Workers(destination) },
                enabled = destination !is Destination.Workers,
            ),
        )
        add(
            MenuEntry(
                label = "Агент",
                onClick = ::openAgent,
                enabled = destination !is Destination.Agent,
            ),
        )
        // No "Выйти" here any more: signing out lives on the profile page behind the drawer's
        // person icon, where a mis-tap while flicking through the menu cannot reach it.
    }

    val userLabel = current.user.fullName?.takeIf { it.isNotBlank() } ?: current.user.login

    // Captured once so every screen opens the two footer pages the same way, each remembering the
    // destination it was opened from.
    val openSettings = { destination = Destination.Settings(destination) }
    val openProfile = { destination = Destination.Profile(destination) }

    when (val where = destination) {
        is Destination.Projects -> ProjectsScreen(
            session = current,
            menu = menu,
            userLabel = userLabel,
            onOpenProject = { destination = Destination.Tasks(it) },
            onOpenSettings = openSettings,
            onOpenProfile = openProfile,
        )

        is Destination.Tasks -> TasksScreen(
            session = current,
            project = where.project,
            menu = menu,
            onOpenTask = { destination = Destination.Task(where.project, it.id) },
            onBack = { destination = Destination.Projects },
            onOpenSettings = openSettings,
            onOpenProfile = openProfile,
        )

        is Destination.Task -> TaskDetailScreen(
            session = current,
            taskId = where.taskId,
            menu = menu,
            onBack = { destination = Destination.Tasks(where.project) },
            onOpenSettings = openSettings,
            onOpenProfile = openProfile,
            onOpenRun = { run, number ->
                destination = Destination.Run(where.project, where.taskId, run.id, number)
            },
        )

        is Destination.Run -> RunDetailScreen(
            session = current,
            taskId = where.taskId,
            runId = where.runId,
            runNumber = where.runNumber,
            menu = menu,
            onBack = { destination = where.from ?: Destination.Task(where.project, where.taskId) },
            onOpenSettings = openSettings,
            onOpenProfile = openProfile,
        )

        is Destination.Interactions -> InteractionsScreen(
            session = current,
            menu = menu,
            focusInteractionId = where.focusInteractionId,
            onBack = { destination = where.from },
            onOpenSettings = { destination = Destination.Settings(where) },
            onOpenProfile = { destination = Destination.Profile(where) },
            // A question knows its project by id and name only, which is all the task screen and
            // the drawer need — the full project row is never loaded just to navigate.
            onOpenTask = { projectId, projectName, taskId ->
                destination = Destination.Task(ProjectDto(id = projectId, name = projectName ?: "Проект"), taskId)
            },
        )

        is Destination.Activities -> ActivitiesScreen(
            session = current,
            menu = menu,
            onBack = { destination = where.from },
            onOpenSettings = { destination = Destination.Settings(where) },
            onOpenProfile = { destination = Destination.Profile(where) },
            onOpenInteraction = { interactionId ->
                destination = Destination.Interactions(from = where, focusInteractionId = interactionId)
            },
            // A feed row knows its project by id and name only — the same shortcut the questions
            // screen takes, since navigating never needs the full project row.
            onOpenRun = { projectId, projectName, taskId, runId ->
                destination = Destination.Run(
                    project = ProjectDto(id = projectId, name = projectName ?: "Проект"),
                    taskId = taskId,
                    runId = runId,
                    runNumber = null,
                    from = where,
                )
            },
        )

        is Destination.Runs -> RunsScreen(
            session = current,
            menu = menu,
            onBack = { destination = where.from },
            onOpenSettings = { destination = Destination.Settings(where) },
            onOpenProfile = { destination = Destination.Profile(where) },
            // A board row knows its project by id and name only — the same shortcut the questions
            // screen takes, since navigating never needs the full project row.
            onOpenRun = { projectId, projectName, taskId, runId ->
                destination = Destination.Run(
                    project = ProjectDto(id = projectId, name = projectName ?: "Проект"),
                    taskId = taskId,
                    runId = runId,
                    runNumber = null,
                    from = where,
                )
            },
        )

        is Destination.Workers -> WorkersScreen(
            session = current,
            menu = menu,
            onBack = { destination = where.from },
            onOpenSettings = { destination = Destination.Settings(where) },
            onOpenProfile = { destination = Destination.Profile(where) },
        )

        is Destination.Agent -> AgentDashboardScreen(
            session = current,
            menu = menu,
            onBack = { destination = where.from },
            onOpenSettings = { destination = Destination.Settings(where) },
            onOpenProfile = { destination = Destination.Profile(where) },
        )

        // The footer icon for the page you are already on is left inert rather than pushing a
        // second copy of it — tapping "Настройки" from settings should do nothing, not deepen the
        // back stack by one indistinguishable screen.
        is Destination.Settings -> SettingsScreen(
            session = current,
            menu = menu,
            onBack = { destination = where.from },
            onOpenSettings = {},
            onOpenProfile = { destination = Destination.Profile(where.from) },
        )

        is Destination.Profile -> ProfileScreen(
            session = current,
            menu = menu,
            onLogout = ::logout,
            onBack = { destination = where.from },
            onOpenSettings = { destination = Destination.Settings(where.from) },
            onOpenProfile = {},
        )
    }
}
