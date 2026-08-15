package com.example.app.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Where a tapped notification wants the app to go.
 *
 * Only the id is required: everything else is context the payload carries so the question's screen
 * can be drawn before the first request comes back — and the app stays able to open a question it
 * knows nothing else about.
 */
data class PushRoute(
    val interactionId: String,
    val projectId: String? = null,
    val projectName: String? = null,
    val taskId: String? = null,
)

/** The address this install is reachable at, as the OS handed it over. */
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

    /** The question a notification asked to open, until [consumeRoute] takes it. */
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
    ) {
        deliverRoute(
            buildMap {
                type?.let { put(KEY_TYPE, it) }
                interactionId?.let { put(KEY_INTERACTION, it) }
                projectId?.let { put(KEY_PROJECT, it) }
                projectName?.let { put(KEY_PROJECT_NAME, it) }
                taskId?.let { put(KEY_TASK, it) }
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
}

/**
 * Reads a notification payload as a destination, or nothing.
 *
 * Deliberately strict about `type`: the same channel will carry other kinds of message eventually,
 * and a payload this function does not understand has to leave the app where it is rather than
 * navigate somewhere half-built.
 */
fun pushRouteFrom(data: Map<String, String>): PushRoute? {
    if (data[Push.KEY_TYPE] != "interaction") return null
    val interactionId = data[Push.KEY_INTERACTION]?.trim().orEmpty()
    if (interactionId.isEmpty()) return null
    return PushRoute(
        interactionId = interactionId,
        projectId = data[Push.KEY_PROJECT]?.takeIf { it.isNotBlank() },
        projectName = data[Push.KEY_PROJECT_NAME]?.takeIf { it.isNotBlank() },
        taskId = data[Push.KEY_TASK]?.takeIf { it.isNotBlank() },
    )
}

/**
 * Asks the OS for permission to notify and for this install's push token, handing the result to
 * [Push.deliverToken]. Called once a session exists — a permission prompt before the user has even
 * signed in is a prompt about nothing.
 *
 * A no-op wherever push does not apply (desktop, browser) or is not configured, so callers never
 * have to ask whether it is available.
 */
expect fun ensurePushRegistration()
