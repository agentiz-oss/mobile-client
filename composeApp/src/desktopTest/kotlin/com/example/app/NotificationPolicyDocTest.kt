package com.example.app

import com.example.app.data.NotificationPolicyDoc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * The structural edits the settings matrix performs on a policy scope. Two invariants carry the
 * weight: "no opinion" is expressed by *absence* (an emptied entry or scope disappears, so the
 * server's resolution falls through to the next level), and unknown keys written by a newer server
 * survive a round trip through this build's editor.
 */
class NotificationPolicyDocTest {

    private val empty = JsonObject(emptyMap())

    @Test
    fun `setting and clearing a channel round-trips to absence`() {
        val withPush = NotificationPolicyDoc.withChannel(empty, "run.failed", "push", "silent")
        assertEquals("silent", NotificationPolicyDoc.channel(withPush, "run.failed", "push"))
        assertNull(NotificationPolicyDoc.channel(withPush, "run.failed", "dashboard"))

        val cleared = NotificationPolicyDoc.withChannel(withPush, "run.failed", "push", null)
        // The emptied entry vanishes with its last channel — the schema never sees `{}`.
        assertTrue(cleared.isEmpty())
    }

    @Test
    fun `mute is a shortcut, not a false flag`() {
        val muted = NotificationPolicyDoc.withMute(empty, true)
        assertTrue(NotificationPolicyDoc.isMuted(muted))
        val unmuted = NotificationPolicyDoc.withMute(muted, false)
        assertFalse(NotificationPolicyDoc.isMuted(unmuted))
        assertTrue(unmuted.isEmpty())
    }

    @Test
    fun `editing one channel leaves everything else in the scope alone`() {
        val scope = buildJsonObject {
            put("mute", true)
            putJsonObject("run.failed") {
                put("push", "off")
                put("dashboard", "off")
            }
            putJsonObject("future.type") { put("push", "on") }
        }

        val edited = NotificationPolicyDoc.withChannel(scope, "run.failed", "push", "on")

        assertEquals("on", NotificationPolicyDoc.channel(edited, "run.failed", "push"))
        assertEquals("off", NotificationPolicyDoc.channel(edited, "run.failed", "dashboard"))
        assertTrue(NotificationPolicyDoc.isMuted(edited))
        // A type this build has never heard of survives the edit untouched.
        assertEquals("on", NotificationPolicyDoc.channel(edited, "future.type", "push"))
    }

    @Test
    fun `an emptied scope disappears from the scope map`() {
        val scopes = buildJsonObject { putJsonObject("p1") { put("mute", true) } }
        val cleared = NotificationPolicyDoc.withScope(
            scopes,
            "p1",
            NotificationPolicyDoc.withMute(NotificationPolicyDoc.scopeOf(scopes, "p1"), false),
        )
        assertTrue(cleared.isEmpty())
    }

    @Test
    fun `effective mirrors the server, explicit entry beating the scope's own mute`() {
        val project = buildJsonObject {
            put("mute", true)
            putJsonObject("run.failed") { put("push", "on") }
        }
        val defaults = buildJsonObject {
            putJsonObject("run.succeeded") { put("push", "silent") }
        }

        // Explicit entry in the nearest scope wins over that scope's mute.
        assertEquals("on", NotificationPolicyDoc.effective(listOf(project, defaults), "on", "run.failed", "push"))
        // Everything else in the muted project is off, whatever defaults say.
        assertEquals("off", NotificationPolicyDoc.effective(listOf(project, defaults), "silent", "run.succeeded", "push"))
        // Without the project scope, defaults answer; without them, the built-in.
        assertEquals("silent", NotificationPolicyDoc.effective(listOf(defaults), "on", "run.succeeded", "push"))
        assertEquals("on", NotificationPolicyDoc.effective(listOf(null), "on", "run.succeeded", "push"))
    }
}
