package com.example.app

import com.example.app.data.InteractionDto
import com.example.app.screens.FieldKind
import com.example.app.screens.buildAnswerContent
import com.example.app.screens.formFields
import com.example.app.screens.missingRequired
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The rendering contract for an agent's question: which schema shapes become which control, and
 * what the answer sent back looks like. The server validates the answer against the same schema, so
 * everything asserted here is about not misrepresenting the question to the person answering it.
 */
class InteractionFormTest {

    private fun interaction(schema: String) = InteractionDto(
        id = "int-1",
        message = "Какую ветку использовать?",
        requestedSchema = Json.parseToJsonElement(schema) as JsonObject,
    )

    @Test
    fun `maps schema types onto the controls the app can render`() {
        val fields = interaction(
            """
            {
              "type": "object",
              "required": ["branch"],
              "properties": {
                "branch":  { "type": "string", "title": "Ветка" },
                "count":   { "type": "integer" },
                "force":   { "type": "boolean", "default": true },
                "mode":    { "type": "string", "enum": ["fast", "safe"] },
                "extras":  { "type": "array" }
              }
            }
            """.trimIndent(),
        ).formFields()

        assertEquals(listOf("branch", "count", "force", "mode", "extras"), fields.map { it.name })
        assertEquals(FieldKind.TEXT, fields[0].kind)
        assertEquals("Ветка", fields[0].title)
        assertTrue(fields[0].required)
        assertEquals(FieldKind.NUMBER, fields[1].kind)
        assertEquals(FieldKind.BOOLEAN, fields[2].kind)
        assertEquals("true", fields[2].initial)
        assertEquals(FieldKind.CHOICE, fields[3].kind)
        assertEquals(listOf("fast", "safe"), fields[3].choices.map { it.label })
        // Anything the app cannot present honestly stays answerable as raw JSON rather than
        // disappearing from the form.
        assertEquals(FieldKind.RAW, fields[4].kind)
    }

    @Test
    fun `builds a typed answer and omits blank optional fields`() {
        val fields = interaction(
            """
            {
              "type": "object",
              "properties": {
                "branch": { "type": "string" },
                "count":  { "type": "integer" },
                "force":  { "type": "boolean" },
                "extras": { "type": "array" },
                "note":   { "type": "string" }
              }
            }
            """.trimIndent(),
        ).formFields()

        val content = buildAnswerContent(
            fields,
            mapOf(
                "branch" to "main",
                "count" to "3",
                "force" to "true",
                "extras" to """["a","b"]""",
                "note" to "   ",
            ),
        )

        assertEquals("main", content["branch"]?.jsonPrimitive?.content)
        assertEquals("3", content["count"]?.jsonPrimitive?.content)
        assertEquals(false, content["count"]?.jsonPrimitive?.isString)
        assertEquals(true, content["force"]?.jsonPrimitive?.booleanOrNull)
        assertEquals("""["a","b"]""", content["extras"].toString())
        // Absence is what "not filled in" means; an empty string is a value a schema can reject.
        assertTrue("note" !in content)
    }

    @Test
    fun `unparseable numbers are sent as typed rather than dropped`() {
        val fields = interaction("""{ "type": "object", "properties": { "count": { "type": "integer" } } }""").formFields()
        val content = buildAnswerContent(fields, mapOf("count" to "три"))
        assertEquals(JsonPrimitive("три"), content["count"])
    }

    @Test
    fun `required fields block sending until they are filled in`() {
        val fields = interaction(
            """
            {
              "type": "object",
              "required": ["branch", "force"],
              "properties": {
                "branch": { "type": "string" },
                "force":  { "type": "boolean" }
              }
            }
            """.trimIndent(),
        ).formFields()

        // A boolean always holds one of its two values, so it can never be the missing one.
        assertEquals(listOf("branch"), missingRequired(fields, mapOf("force" to "false")).map { it.name })
        assertTrue(missingRequired(fields, mapOf("branch" to "main", "force" to "false")).isEmpty())
    }

    /**
     * The shape ACP agents actually send: a readable title next to the compact value the answer has
     * to carry. Submitting the title is exactly what the server's validator rejects.
     */
    @Test
    fun `renders oneOf options by title and answers with their const`() {
        val fields = interaction(
            """
            {
              "type": "object",
              "properties": {
                "next_action": {
                  "type": "string",
                  "title": "Что дальше",
                  "oneOf": [
                    { "const": "report", "title": "Показать краткий отчёт" },
                    { "const": "repeat", "title": "Повторить замер" }
                  ]
                }
              }
            }
            """.trimIndent(),
        ).formFields()

        assertEquals(FieldKind.CHOICE, fields[0].kind)
        assertEquals(listOf("Показать краткий отчёт", "Повторить замер"), fields[0].choices.map { it.label })
        // Options are held by position, so the value sent back is the schema's own, untouched.
        assertEquals(JsonPrimitive("repeat"), buildAnswerContent(fields, mapOf("next_action" to "1"))["next_action"])
    }

    @Test
    fun `keeps enum values typed instead of sending the text of their label`() {
        val fields = interaction(
            """{ "type": "object", "properties": { "attempts": { "type": "integer", "enum": [1, 2] } } }""",
        ).formFields()

        val content = buildAnswerContent(fields, mapOf("attempts" to "1"))
        assertEquals(false, content["attempts"]?.jsonPrimitive?.isString)
        assertEquals("2", content["attempts"]?.jsonPrimitive?.content)
    }

    /**
     * Codex splits a question that allows a free-text answer into the choice plus a `__other`
     * sibling. Both belong to one control here, and the answer carries the sibling *instead of* the
     * choice — the server drops the choice from validation in exactly that case.
     */
    @Test
    fun `folds a Codex Other field into the question it belongs to`() {
        val fields = interaction(
            """
            {
              "type": "object",
              "properties": {
                "next_action": {
                  "type": "string",
                  "oneOf": [{ "const": "report" }, { "const": "repeat" }],
                  "_meta": { "codex": { "isOther": true } }
                },
                "next_action__other": {
                  "type": "string",
                  "_meta": { "codex": { "questionId": "next_action", "isOtherAnswer": true } }
                }
              }
            }
            """.trimIndent(),
        ).formFields()

        assertEquals(listOf("next_action"), fields.map { it.name })
        assertEquals("next_action__other", fields[0].otherName)

        val content = buildAnswerContent(
            fields,
            mapOf("next_action" to "other", "next_action__other" to "Проверь ещё место на диске"),
        )
        assertEquals("Проверь ещё место на диске", content["next_action__other"]?.jsonPrimitive?.content)
        assertTrue("next_action" !in content)
    }

    @Test
    fun `a choice with neither an option nor an Other answer blocks sending`() {
        val fields = interaction(
            """
            {
              "type": "object",
              "properties": {
                "next_action": {
                  "type": "string",
                  "oneOf": [{ "const": "report" }],
                  "_meta": { "codex": { "isOther": true } }
                },
                "next_action__other": {
                  "type": "string",
                  "_meta": { "codex": { "questionId": "next_action", "isOtherAnswer": true } }
                }
              }
            }
            """.trimIndent(),
        ).formFields()

        // Optional in the schema, but an empty form is not an answer: the agent would read it as
        // "no preference" and carry on.
        assertEquals(listOf("next_action"), missingRequired(fields, emptyMap()).map { it.name })
        assertTrue(missingRequired(fields, mapOf("next_action" to "0")).isEmpty())
        assertTrue(missingRequired(fields, mapOf("next_action__other" to "свой вариант")).isEmpty())
    }

    @Test
    fun `a schema without properties renders no form at all`() {
        assertTrue(interaction("""{ "type": "object" }""").formFields().isEmpty())
    }
}
