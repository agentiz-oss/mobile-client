package com.example.app.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Structural edits over one scope of the notification-policy document.
 *
 * A scope is a [JsonObject] mixing `mute: true` with per-type entries
 * (`"run.failed": {"push": "off"}`), exactly as the server stores it — kept as JSON rather than a
 * DTO so a document written by a newer server round-trips without losing keys this build does not
 * know. Every mutation returns a new object; nothing here touches the network.
 *
 * Resolution preview mirrors the server (`policySettings.ts`): inside a scope an explicit type
 * entry wins, then the scope's `mute`, then the next scope, then the built-in default.
 */
object NotificationPolicyDoc {

    /** The explicit value of one channel in one scope, or null when the scope says nothing. */
    fun channel(scope: JsonObject?, type: String, channel: String): String? =
        (scope?.get(type) as? JsonObject)?.get(channel)?.jsonPrimitive?.contentOrNull

    fun isMuted(scope: JsonObject?): Boolean =
        (scope?.get("mute") as? JsonPrimitive)?.booleanOrNull == true

    /**
     * Sets or clears one channel of one type. `value = null` removes the override, and an entry
     * left empty disappears with it — "no opinion" is expressed by absence, never by an empty
     * object the schema would still have to allow.
     */
    fun withChannel(scope: JsonObject, type: String, channel: String, value: String?): JsonObject {
        val entry = (scope[type] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        if (value == null) entry.remove(channel) else entry[channel] = JsonPrimitive(value)
        val next = scope.toMutableMap()
        if (entry.isEmpty()) next.remove(type) else next[type] = JsonObject(entry)
        return JsonObject(next)
    }

    /** Sets or clears the scope's mute shortcut. Absence, not `false`, is the off state. */
    fun withMute(scope: JsonObject, muted: Boolean): JsonObject {
        val next = scope.toMutableMap()
        if (muted) next["mute"] = JsonPrimitive(true) else next.remove("mute")
        return JsonObject(next)
    }

    /** One named scope out of a `projects`/`pipelines` map; an absent one reads as empty. */
    fun scopeOf(scopes: JsonObject, id: String): JsonObject =
        (scopes[id] as? JsonObject) ?: JsonObject(emptyMap())

    /** Puts the scope back, dropping it entirely when it says nothing — same absence rule as above. */
    fun withScope(scopes: JsonObject, id: String, scope: JsonObject): JsonObject {
        val next = scopes.toMutableMap()
        if (scope.isEmpty()) next.remove(id) else next[id] = scope
        return JsonObject(next)
    }

    /**
     * What would actually be delivered for one type × channel, walking scope by scope the way the
     * server does. Used to render "как в общих: тихо" hints, so it has to agree with the server's
     * resolution or the hint lies.
     */
    fun effective(
        scopes: List<JsonObject?>,
        builtin: String,
        type: String,
        channelName: String,
    ): String {
        for (scope in scopes) {
            if (scope == null) continue
            channel(scope, type, channelName)?.let { return it }
            if (isMuted(scope)) return "off"
        }
        return builtin
    }
}

/** The scopes a policy document offers, in resolution order, for one project. */
fun NotificationPolicyDto.scopesFor(projectId: String?): List<JsonObject?> = listOfNotNull(
    projectId?.let { projects[it]?.jsonObject },
    defaults,
)
