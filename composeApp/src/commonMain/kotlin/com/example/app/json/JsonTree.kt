package com.example.app.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The shape behind [JsonViewer]: a payload of unknown schema turned into rows a person can open one
 * at a time, and copy any part of.
 *
 * Everything here is pure so the reading rules — what a collapsed branch says about itself, what a
 * copy actually puts on the clipboard — are testable without a composition. The viewer only decides
 * which of these rows are currently visible.
 */

/** Pretty form, shared with the copy action: what a person pastes should be readable. */
internal val jsonPretty = Json { prettyPrint = true }

/**
 * One row of the tree: a value plus how it is addressed.
 *
 * [path] is the row's identity — the expand state and the "скопировано" flash are keyed by it, and
 * it survives a refresh that hands the viewer an equal-but-new element, so an open branch stays
 * open while a run is still streaming into it.
 */
internal data class JsonNode(
    val path: String,
    val key: String?,
    val index: Int?,
    val element: JsonElement,
    val depth: Int,
) {
    /** Objects and arrays are the only rows that open; a scalar is already everything it has. */
    val isContainer: Boolean get() = element is JsonObject || element is JsonArray

    /** How the row names itself: an object's key, an array's position, or nothing at the root. */
    val label: String? get() = key ?: index?.let { "[$it]" }
}

/** The children of a container, in document order; empty for scalars. */
internal fun JsonNode.children(): List<JsonNode> = when (val value = element) {
    is JsonObject -> value.entries.map { (name, child) ->
        JsonNode(
            path = "$path/${name.escapeSegment()}",
            key = name,
            index = null,
            element = child,
            depth = depth + 1,
        )
    }
    is JsonArray -> value.mapIndexed { position, child ->
        JsonNode(path = "$path/$position", key = null, index = position, element = child, depth = depth + 1)
    }
    else -> emptyList()
}

/** `/` separates path segments, so a key containing one must not forge a second segment. */
private fun String.escapeSegment(): String = replace("~", "~0").replace("/", "~1")

/** The root of a document, from which [children] unfolds the rest. */
internal fun jsonRoot(element: JsonElement, label: String? = null): JsonNode =
    JsonNode(path = "$", key = label, index = null, element = element, depth = 0)

/**
 * How many children a container has, said in words rather than as a bare number: a collapsed row
 * has to answer "стоит ли открывать" on its own.
 */
internal fun JsonElement.countLabel(): String? = when (this) {
    is JsonObject -> plural(size, "поле", "поля", "полей")
    is JsonArray -> plural(size, "элемент", "элемента", "элементов")
    else -> null
}

private fun plural(count: Int, one: String, few: String, many: String): String {
    val mod100 = count % 100
    val mod10 = count % 10
    val word = when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
    return "$count $word"
}

/**
 * The one-line stand-in a collapsed row shows: enough of the value to recognise it, never enough to
 * wrap. Containers keep their braces so nesting is visible in the preview itself, and the budget is
 * spent depth-first — a wide object shows its first keys rather than an even sliver of all of them.
 */
internal fun JsonElement.preview(budget: Int = PREVIEW_BUDGET): String {
    val out = StringBuilder()
    appendPreview(out, budget)
    return out.toString()
}

private const val PREVIEW_BUDGET = 64
private const val ELLIPSIS = "…"

private fun JsonElement.appendPreview(out: StringBuilder, budget: Int) {
    when (this) {
        is JsonNull -> out.append("null")
        is JsonPrimitive -> out.append(if (isString) quote(content, budget) else content.truncate(budget))
        is JsonArray -> {
            if (isEmpty()) {
                out.append("[]")
                return
            }
            out.append('[')
            forEachIndexed { position, child ->
                if (position > 0) out.append(", ")
                if (out.length >= budget) {
                    out.append(ELLIPSIS)
                    out.append(']')
                    return
                }
                child.appendPreview(out, budget)
            }
            out.append(']')
        }
        is JsonObject -> {
            if (isEmpty()) {
                out.append("{}")
                return
            }
            out.append('{')
            entries.forEachIndexed { position, (name, child) ->
                if (position > 0) out.append(", ")
                if (out.length >= budget) {
                    out.append(ELLIPSIS)
                    out.append('}')
                    return
                }
                out.append(name).append(": ")
                child.appendPreview(out, budget)
            }
            out.append('}')
        }
    }
}

/** A string value as it reads in a preview: quoted, newlines flattened, cut to the budget. */
private fun quote(content: String, budget: Int): String {
    val flat = content.replace('\n', ' ').replace('\r', ' ')
    return "\"" + flat.truncate(budget) + "\""
}

private fun String.truncate(limit: Int): String =
    if (length <= limit) this else take(limit) + ELLIPSIS

/**
 * What the copy action puts on the clipboard for one node.
 *
 * A string leaf copies its **content**, not its JSON literal: the reason to copy `errorMessage` is
 * to paste the message somewhere, and quotes plus `\n` escapes would be exactly wrong there.
 * Everything else copies as pretty JSON, which is also valid input for whatever it gets pasted into.
 */
internal fun JsonElement.copyText(): String = when {
    this is JsonPrimitive && this !is JsonNull && isString -> content
    this is JsonPrimitive -> content
    else -> jsonPretty.encodeToString(JsonElement.serializer(), this)
}
