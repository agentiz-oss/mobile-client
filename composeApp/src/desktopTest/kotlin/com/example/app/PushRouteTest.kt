package com.example.app

import com.example.app.push.Push
import com.example.app.push.PushRoute
import com.example.app.push.pushRouteFrom
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What a notification has to say for the app to act on it.
 *
 * This is the one piece of the push path that is neither Android nor iOS: both platforms hand the
 * payload over as strings and everything after that — deciding where to go, and going there exactly
 * once — is shared.
 */
class PushRouteTest {

    @AfterTest
    fun clearRoute() {
        Push.consumeRoute()
    }

    private fun payload(vararg pairs: Pair<String, String>) = mapOf(
        "type" to "interaction",
        "interactionId" to "int-1",
        *pairs,
    )

    @Test
    fun `reads the question and its context out of the payload`() {
        val route = pushRouteFrom(
            payload(
                "runId" to "run-1",
                "projectId" to "proj-1",
                "projectName" to "Agentiz",
                "taskId" to "task-1",
            ),
        )

        assertEquals(
            PushRoute(interactionId = "int-1", projectId = "proj-1", projectName = "Agentiz", taskId = "task-1"),
            route,
        )
    }

    @Test
    fun `the id is the only thing it cannot do without`() {
        // A payload from a server that has not been told about the extra context still opens the
        // right question.
        assertEquals(PushRoute(interactionId = "int-1"), pushRouteFrom(mapOf("type" to "interaction", "interactionId" to "int-1")))
        assertNull(pushRouteFrom(mapOf("type" to "interaction", "interactionId" to "  ")))
        assertNull(pushRouteFrom(mapOf("type" to "interaction")))
    }

    @Test
    fun `a payload of another kind leaves the app where it is`() {
        // Android hands over the whole extras bundle, which is full of the system's own keys, and
        // the channel will carry other message types eventually.
        assertNull(pushRouteFrom(mapOf("google.sent_time" to "1", "from" to "/topics/x")))
        assertNull(pushRouteFrom(payload().minus("type").plus("type" to "run_finished")))
        assertNull(pushRouteFrom(emptyMap()))
    }

    @Test
    fun `a route is delivered once and only once`() {
        Push.deliverRoute(payload())
        assertEquals("int-1", Push.consumeRoute()?.interactionId)
        // Without this, every recomposition would re-navigate to the same question and the user
        // could never leave it.
        assertNull(Push.consumeRoute())
    }

    @Test
    fun `an unrecognised payload does not clear a route already waiting`() {
        Push.deliverRoute(payload())
        Push.deliverRoute(mapOf("type" to "something-else"))
        assertEquals("int-1", Push.consumeRoute()?.interactionId)
    }

    @Test
    fun `iOS delivers the same route field by field`() {
        Push.deliverRouteFields(
            type = "interaction",
            interactionId = "int-9",
            projectId = "proj-9",
            projectName = "Agentiz",
            taskId = null,
        )
        assertEquals(
            PushRoute(interactionId = "int-9", projectId = "proj-9", projectName = "Agentiz", taskId = null),
            Push.consumeRoute(),
        )
    }

    @Test
    fun `a blank token is not an address`() {
        Push.deliverToken("   ", "android")
        assertNull(Push.currentToken())
        Push.deliverToken("real-token", "android")
        assertEquals("real-token", Push.currentToken())
    }
}
