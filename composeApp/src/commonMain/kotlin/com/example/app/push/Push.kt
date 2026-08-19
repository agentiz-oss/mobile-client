package com.example.app.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Where a tapped notification wants the app to go.
 *
 * Two kinds travel today. [Question] is the legacy `type=interaction` payload — an agent stopped
 * and waits, the app opens that question. [Activity] is everything else the server announces
 * (`type=activity`): a review waiting, a failed push, a finished run — the app opens the run it
 * belongs to when the payload names one, and the activities feed otherwise.
 *
 * Only the ids are required: the rest is context the payload carries so the target screen can be
 * drawn before the first request comes back.
 */
sealed interface PushRoute {
    data class Question(
        val interactionId: String,
        val projectId: String? = null,
        val projectName: String? = null,
        val taskId: String? = null,
    ) : PushRoute

    data class Activity(
        val activityType: String,
        val activityId: String? = null,
        val runId: String? = null,
        val taskId: String? = null,
        val projectId: String? = null,
        val projectName: String? = null,
        val proposalId: String? = null,
    ) : PushRoute
}

/** The address this install is reachable at, as the OS handed it over: an FCM token, either platform. */
data class PushRegistration(val token: String, val platform: String)

/**
 * The seam between the platforms' notification machinery and the Compose tree.
 *
 * Both directions of push are asynchronous and start *outside* the app: a token can arrive before
 * anyone has signed in, and a notification can be tapped while the process is not running at all.
 * So neither is delivered as a call into a screen — each is parked in a flow that [com.example.app.App]
 * watches and drains when it is able to act on it.
 *
 * A global object rather than something scoped: on both platforms the callbacks are static entry
 * points (a Firebase service, an app delegate) with no route to whatever composition happens to be
 * alive.
 */
object Push {
    private val _route = MutableStateFlow<PushRoute?>(null)
    private val _registration = MutableStateFlow<PushRegistration?>(null)

    /** The destination a notification asked to open, until [consumeRoute] takes it. */
    val route: StateFlow<PushRoute?> = _route

    /** The push token to register with the server, once there is a session to register it under. */
    val registration: StateFlow<PushRegistration?> = _registration

    /** Android: the whole `data` map of the message, straight from the intent extras. */
    fun deliverRoute(data: Map<String, String>) {
        pushRouteFrom(data)?.let { _route.value = it }
    }

    /**
     * iOS: the same, spelled out field by field. Swift hands over an `[AnyHashable: Any]` userInfo,
     * and pulling the strings out on that side is less fragile than bridging a whole dictionary
     * into Kotlin.
     */
    fun deliverRouteFields(
        type: String?,
        interactionId: String?,
        projectId: String?,
        projectName: String?,
        taskId: String?,
        activityType: String? = null,
        activityId: String? = null,
        runId: String? = null,
    ) {
        deliverRoute(
            buildMap {
                type?.let { put(KEY_TYPE, it) }
                interactionId?.let { put(KEY_INTERACTION, it) }
                projectId?.let { put(KEY_PROJECT, it) }
                projectName?.let { put(KEY_PROJECT_NAME, it) }
                taskId?.let { put(KEY_TASK, it) }
                activityType?.let { put(KEY_ACTIVITY_TYPE, it) }
                activityId?.let { put(KEY_ACTIVITY_ID, it) }
                runId?.let { put(KEY_RUN, it) }
            },
        )
    }

    /** Taken exactly once: a route that has been navigated to must not re-open on the next frame. */
    fun consumeRoute(): PushRoute? = _route.value?.also { _route.value = null }

    fun deliverToken(token: String, platform: String) {
        if (token.isBlank()) return
        _registration.value = PushRegistration(token = token, platform = platform)
    }

    /** What the app last registered, so signing out can tell the server to forget this device. */
    fun currentToken(): String? = _registration.value?.token

    internal const val KEY_TYPE = "type"
    internal const val KEY_INTERACTION = "interactionId"
    internal const val KEY_PROJECT = "projectId"
    internal const val KEY_PROJECT_NAME = "projectName"
    internal const val KEY_TASK = "taskId"
    internal const val KEY_ACTIVITY_TYPE = "activityType"
    internal const val KEY_ACTIVITY_ID = "activityId"
    internal const val KEY_RUN = "runId"
    internal const val KEY_PROPOSAL = "proposalId"
}

/**
 * Reads a notification payload as a destination, or nothing.
 *
 * Deliberately strict about `type`: a payload this function does not understand has to leave the
 * app where it is rather than navigate somewhere half-built — which is also the soft-degradation
 * contract with older servers and newer payload kinds alike.
 */
fun pushRouteFrom(data: Map<String, String>): PushRoute? {
    fun field(key: String): String? = data[key]?.trim()?.takeIf { it.isNotEmpty() }
    return when (data[Push.KEY_TYPE]) {
        "interaction" -> {
            val interactionId = field(Push.KEY_INTERACTION) ?: return null
            PushRoute.Question(
                interactionId = interactionId,
                projectId = field(Push.KEY_PROJECT),
                projectName = field(Push.KEY_PROJECT_NAME),
                taskId = field(Push.KEY_TASK),
            )
        }
        "activity" -> {
            val activityType = field(Push.KEY_ACTIVITY_TYPE) ?: return null
            PushRoute.Activity(
                activityType = activityType,
                activityId = field(Push.KEY_ACTIVITY_ID),
                runId = field(Push.KEY_RUN),
                taskId = field(Push.KEY_TASK),
                projectId = field(Push.KEY_PROJECT),
                projectName = field(Push.KEY_PROJECT_NAME),
                proposalId = field(Push.KEY_PROPOSAL),
            )
        }
        else -> null
    }
}

/**
 * Sets the number the launcher draws on the app icon.
 *
 * The server puts a badge count into every notification, which means the number is only ever
 * corrected when the *next* one arrives — act on everything and the icon keeps claiming there is
 * work. So the app owns the badge too, from the same actionable count the drawer already polls;
 * the push value is then just what the number is between two polls.
 *
 * A no-op wherever the platform has no such thing (Android draws its own from the notifications,
 * desktop and browser have none).
 */
expect fun setAppBadge(count: Int)

/**
 * Asks the OS for permission to notify and for this install's push token, handing the result to
 * [Push.deliverToken]. Called once a session exists — a permission prompt before the user has even
 * signed in is a prompt about nothing.
 *
 * A no-op wherever push does not apply (desktop, browser) or is not configured, so callers never
 * have to ask whether it is available.
 */
expect fun ensurePushRegistration()
