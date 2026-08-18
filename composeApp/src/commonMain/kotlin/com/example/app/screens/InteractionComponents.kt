package com.example.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.example.app.components.AppButton
import com.example.app.components.AppTextField
import com.example.app.data.InteractionDto
import com.example.app.theme.AppTheme
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.Json

/**
 * An agent's question, rendered from the JSON Schema the server sent with it.
 *
 * The schema is authored by whatever agent asked, so the app deliberately renders the handful of
 * shapes a phone can present honestly — text, number, yes/no, a fixed list of choices — and falls
 * back to a raw JSON box for anything else rather than dropping the field. The server validates the
 * answer against the same schema either way, so a fallback answer is checked exactly as strictly as
 * a rendered one; what the fallback avoids is *silently* submitting a form the user could not see.
 */

/** The kinds of field the form renders natively; everything else becomes [FieldKind.RAW]. */
internal enum class FieldKind { TEXT, NUMBER, BOOLEAN, CHOICE, RAW }

/** The value a [FieldKind.CHOICE] field holds while "Другое" is selected instead of an option. */
internal const val OTHER_SELECTION = "other"

/**
 * One option of a choice field. [value] is the JSON the answer must carry — a schema commonly pairs
 * a compact `const` with a human-readable `title`, and submitting the title is exactly what the
 * server rejects.
 */
internal data class InteractionChoice(
    val value: JsonElement,
    val label: String,
    val description: String?,
)

/**
 * One property of `requestedSchema.properties`, reduced to what the renderer needs.
 *
 * [otherName] is the sibling property a free-text "Other" answer goes into: Codex splits such a
 * question into the choice itself plus a `<name>__other` field, and renders as one control here.
 */
internal data class InteractionField(
    val name: String,
    val title: String,
    val description: String?,
    val kind: FieldKind,
    val choices: List<InteractionChoice>,
    val otherName: String?,
    val required: Boolean,
    val initial: String,
)

private val compactJson = Json

/** A schema value as editable text: primitives as themselves, anything else as its JSON. */
private fun JsonElement.asText(): String = when (this) {
    is JsonPrimitive -> if (this is JsonNull) "" else content
    else -> compactJson.encodeToString(JsonElement.serializer(), this)
}

private fun JsonObject.stringField(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() }

/** The `_meta.codex` annotations Codex attaches to the two halves of an "Other" question. */
private fun JsonObject.codexMeta(): JsonObject? = (this["_meta"] as? JsonObject)?.get("codex") as? JsonObject

/** The question this property carries the free-text answer for, if it is such a sibling. */
private fun JsonObject.otherAnswerFor(): String? {
    val codex = codexMeta() ?: return null
    if ((codex["isOtherAnswer"] as? JsonPrimitive)?.booleanOrNull != true) return null
    return (codex["questionId"] as? JsonPrimitive)?.takeIf { it.isString }?.content
}

/**
 * The options of a choice field. `oneOf` comes first because that is what ACP agents emit: a
 * compact `const` to submit next to the title a person reads. A bare `enum` keeps its values as
 * they are typed in the schema — a numeric option must not turn into the string of its digits.
 */
private fun choicesOf(definition: JsonObject): List<InteractionChoice> {
    val oneOf = (definition["oneOf"] as? JsonArray)?.mapNotNull { entry ->
        val option = entry as? JsonObject ?: return@mapNotNull null
        val const = option["const"] ?: return@mapNotNull null
        InteractionChoice(
            value = const,
            label = option.stringField("title") ?: const.asText(),
            description = option.stringField("description"),
        )
    } ?: emptyList()
    if (oneOf.isNotEmpty()) return oneOf
    return (definition["enum"] as? JsonArray)
        ?.map { InteractionChoice(value = it, label = it.asText(), description = null) }
        ?: emptyList()
}

/** The form to render for this question, in the order the schema declares its properties. */
internal fun InteractionDto.formFields(): List<InteractionField> {
    val properties = requestedSchema["properties"] as? JsonObject ?: return emptyList()
    val required = (requestedSchema["required"] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.content }
        ?.toSet()
        ?: emptySet()

    // A `<question>__other` property is not a question of its own — it is the "Другое" branch of
    // the one it names, and is rendered inside it rather than as a second, cryptically named field.
    val otherOf = properties.mapNotNull { (name, raw) ->
        (raw as? JsonObject)?.otherAnswerFor()?.let { question -> question to name }
    }.toMap()
    val otherNames = otherOf.values.toSet()

    return properties.mapNotNull { (name, raw) ->
        if (name in otherNames) return@mapNotNull null
        val definition = raw as? JsonObject ?: JsonObject(emptyMap())
        // `type` may legitimately be a list ("string" or null); only a plain scalar is rendered
        // natively, the union falls through to the raw box.
        val type = definition.stringField("type")
        val choices = choicesOf(definition)
        val kind = when {
            choices.isNotEmpty() -> FieldKind.CHOICE
            type == "boolean" -> FieldKind.BOOLEAN
            type == "number" || type == "integer" -> FieldKind.NUMBER
            // An untyped property is a free-text one in practice; every ACP form seen so far names
            // its type, and guessing "raw JSON" for the omission would be hostile to the reader.
            type == "string" || type == null -> FieldKind.TEXT
            else -> FieldKind.RAW
        }
        val default = definition["default"]
        InteractionField(
            name = name,
            title = definition.stringField("title") ?: name,
            description = definition.stringField("description"),
            kind = kind,
            choices = choices,
            otherName = otherOf[name],
            required = name in required,
            initial = when {
                // A choice is held as the index of the picked option, so the answer can carry the
                // option's own JSON value rather than whatever its label happened to say.
                kind == FieldKind.CHOICE ->
                    choices.indexOfFirst { it.value == default }.takeIf { it >= 0 }?.toString() ?: ""
                default != null -> default.asText()
                kind == FieldKind.BOOLEAN -> "false"
                else -> ""
            },
        )
    }
}

/** The free text typed into a choice field's "Другое" branch, if there is one. */
private fun InteractionField.otherText(values: Map<String, String>): String =
    otherName?.let { values[it] }?.trim().orEmpty()

/** The option currently picked, or null while none is (including when "Другое" is). */
private fun InteractionField.pickedChoice(values: Map<String, String>): InteractionChoice? =
    (values[name] ?: initial).toIntOrNull()?.let { choices.getOrNull(it) }

/**
 * The `content` object for an `accept`. A blank optional field is left out entirely rather than
 * submitted as an empty string: `""` is a value a schema can reject, absence is what "not filled in"
 * actually means.
 */
internal fun buildAnswerContent(fields: List<InteractionField>, values: Map<String, String>): JsonObject =
    buildJsonObject {
        fields.forEach { field ->
            val text = values[field.name] ?: field.initial
            if (field.kind == FieldKind.CHOICE) {
                val other = field.otherText(values)
                // "Другое" replaces the option instead of accompanying it: the agent reads the
                // sibling field, and the choice — optional in exactly this schema — would only give
                // the server's validator an empty value to reject.
                if (field.otherName != null && (text == OTHER_SELECTION || text.isBlank()) && other.isNotEmpty()) {
                    put(field.otherName, JsonPrimitive(other))
                    return@forEach
                }
                // The option's own JSON, not its label and not a string of it: `oneOf` consts and
                // numeric `enum` values are compared by value on the server.
                field.pickedChoice(values)?.let { put(field.name, it.value) }
                return@forEach
            }
            if (field.kind != FieldKind.BOOLEAN && text.isBlank()) return@forEach
            when (field.kind) {
                FieldKind.BOOLEAN -> put(field.name, JsonPrimitive(text.equals("true", ignoreCase = true)))
                FieldKind.NUMBER -> {
                    val number: Number? = text.toLongOrNull() ?: text.toDoubleOrNull()
                    // Unparseable text is sent as typed rather than dropped: the server's schema
                    // error names the field, which is a better answer than a silently missing key.
                    put(field.name, if (number != null) JsonPrimitive(number) else JsonPrimitive(text))
                }
                FieldKind.RAW -> put(
                    field.name,
                    runCatching { compactJson.parseToJsonElement(text) }.getOrElse { JsonPrimitive(text) },
                )
                else -> put(field.name, JsonPrimitive(text))
            }
        }
    }

/**
 * Fields that still block sending. Booleans always hold a value, so they can never be missing; a
 * choice always does, whether the schema marks it required or not — an unanswered question submitted
 * as an empty form is accepted by the server and read by the agent as "no preference", which is a
 * worse outcome than a disabled button.
 */
internal fun missingRequired(fields: List<InteractionField>, values: Map<String, String>): List<InteractionField> =
    fields.filter { field ->
        when (field.kind) {
            FieldKind.BOOLEAN -> false
            FieldKind.CHOICE -> field.pickedChoice(values) == null && field.otherText(values).isEmpty()
            else -> field.required && (values[field.name] ?: field.initial).isBlank()
        }
    }

/**
 * One unanswered question with its form and the three ways out of it.
 *
 * `Ответить` sends the form, `Пропустить` (decline) and `Отменить` (cancel) let the agent continue
 * without one — all three unblock the run, which is why none of them is hidden behind a menu.
 */
@Composable
internal fun InteractionCard(
    interaction: InteractionDto,
    busy: Boolean,
    onAnswer: (action: String, content: JsonObject?) -> Unit,
    showContext: Boolean = false,
    onOpenTask: (() -> Unit)? = null,
) {
    val fields = remember(interaction.id, interaction.requestedSchema) { interaction.formFields() }
    // Keyed by the question, not by the list position: the task screen re-polls every two seconds
    // and half-typed input must survive every one of those refreshes.
    val values = remember(interaction.id) {
        mutableStateMapOf<String, String>().apply { fields.forEach { put(it.name, it.initial) } }
    }
    val missing = missingRequired(fields, values)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // A pending question is the one thing on the screen that is actively holding work up,
            // so it gets the accent border rather than the neutral one every other card wears.
            .border(1.dp, AppTheme.Primary, RoundedCornerShape(AppTheme.Radius))
            .background(AppTheme.RunCard, RoundedCornerShape(AppTheme.Radius))
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Агент ждёт ответа", style = AppTheme.Label, color = AppTheme.Foreground)
            val stamp = formatTimestamp(interaction.createdAt)
            if (stamp != null) {
                Text(text = stamp, style = AppTheme.Label, color = AppTheme.Muted)
            }
        }

        val origin = listOfNotNull(
            interaction.stageRole?.takeIf { it.isNotBlank() },
            interaction.source.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        if (origin.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(text = origin, style = AppTheme.Label, color = AppTheme.Muted)
        }

        if (showContext) {
            val context = listOfNotNull(
                interaction.projectName?.takeIf { it.isNotBlank() },
                interaction.taskTitle?.takeIf { it.isNotBlank() },
            ).joinToString(" → ")
            if (context.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = context,
                    style = AppTheme.Label,
                    color = if (onOpenTask != null) AppTheme.Primary else AppTheme.Muted,
                    modifier = if (onOpenTask != null) {
                        Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onOpenTask)
                    } else {
                        Modifier
                    },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(text = interaction.message, style = AppTheme.Body, color = AppTheme.Foreground)

        fields.forEach { field ->
            Spacer(Modifier.height(16.dp))
            FieldEditor(
                field = field,
                value = values[field.name] ?: field.initial,
                otherValue = field.otherName?.let { values[it] }.orEmpty(),
                enabled = !busy,
                onValueChange = { values[field.name] = it },
                onOtherChange = { text -> field.otherName?.let { values[it] = text } },
            )
        }

        if (fields.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Вопрос без полей — подтвердите или откажитесь.",
                style = AppTheme.Label,
                color = AppTheme.Muted,
            )
        }

        val deadline = formatTimestamp(interaction.expiresAt)
        if (deadline != null) {
            Spacer(Modifier.height(12.dp))
            // The run is cancelled when this passes, so it is a consequence, not a footnote.
            val left = formatRemaining(interaction.expiresAt)?.let { " (осталось $it)" } ?: ""
            Text(text = "Ответ ждут до $deadline$left", style = AppTheme.Label, color = AppTheme.Muted)
        }

        if (missing.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Заполните: ${missing.joinToString(", ") { it.title }}",
                style = AppTheme.Label,
                color = AppTheme.Muted,
            )
        }

        Spacer(Modifier.height(16.dp))
        AppButton(
            text = if (busy) "…" else "Ответить",
            onClick = { onAnswer("accept", buildAnswerContent(fields, values)) },
            enabled = !busy && missing.isEmpty(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryAction(
                text = "Пропустить",
                enabled = !busy,
                onClick = { onAnswer("decline", null) },
                modifier = Modifier.weight(1f),
            )
            SecondaryAction(
                text = "Отменить",
                enabled = !busy,
                onClick = { onAnswer("cancel", null) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FieldEditor(
    field: InteractionField,
    value: String,
    otherValue: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onOtherChange: (String) -> Unit,
) {
    val label = if (field.required) "${field.title} *" else field.title
    when (field.kind) {
        FieldKind.BOOLEAN -> Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = label, style = AppTheme.Label, color = AppTheme.Foreground)
            FieldHint(field.description)
            Spacer(Modifier.height(8.dp))
            OptionPills(
                options = listOf("true" to "Да", "false" to "Нет"),
                selected = value,
                enabled = enabled,
                onSelect = onValueChange,
            )
        }

        FieldKind.CHOICE -> Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = label, style = AppTheme.Label, color = AppTheme.Foreground)
            FieldHint(field.description)
            Spacer(Modifier.height(8.dp))
            // Pills are addressed by position, so an option's own value — which may be a number or
            // any other JSON — never has to survive a round trip through the UI as text.
            val options = field.choices.mapIndexed { index, choice -> index.toString() to choice.label } +
                if (field.otherName != null) listOf(OTHER_SELECTION to "Другое") else emptyList()
            OptionPills(
                options = options,
                selected = value,
                enabled = enabled,
                onSelect = onValueChange,
            )
            // Only what the picked option says about itself; the descriptions of the others would
            // turn a question into a wall of text on a phone.
            FieldHint(field.choices.getOrNull(value.toIntOrNull() ?: -1)?.description)
            if (field.otherName != null && value == OTHER_SELECTION) {
                Spacer(Modifier.height(8.dp))
                AppTextField(
                    label = "Свой вариант",
                    value = otherValue,
                    onValueChange = onOtherChange,
                    placeholder = "Ответ…",
                    enabled = enabled,
                    imeAction = ImeAction.Done,
                )
            }
        }

        else -> Column(modifier = Modifier.fillMaxWidth()) {
            AppTextField(
                label = label,
                value = value,
                onValueChange = onValueChange,
                placeholder = when (field.kind) {
                    FieldKind.NUMBER -> "Число"
                    FieldKind.RAW -> "JSON"
                    else -> "Ответ…"
                },
                enabled = enabled,
                imeAction = ImeAction.Done,
                keyboardType = if (field.kind == FieldKind.NUMBER) KeyboardType.Number else KeyboardType.Text,
                // A raw field holds a JSON object or array, which never fits on one line.
                minLines = if (field.kind == FieldKind.RAW) 3 else 1,
            )
            FieldHint(field.description)
        }
    }
}

@Composable
private fun FieldHint(description: String?) {
    val text = description?.takeIf { it.isNotBlank() } ?: return
    Spacer(Modifier.height(4.dp))
    Text(text = text, style = AppTheme.Label, color = AppTheme.Muted)
}

/** A fixed set of answers as tappable pills — used for both `enum` and yes/no. */
@Composable
private fun OptionPills(
    options: List<Pair<String, String>>,
    selected: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Text(
                text = label,
                style = AppTheme.Label,
                color = if (active) AppTheme.PrimaryForeground else AppTheme.Foreground,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (active) AppTheme.Primary else AppTheme.Background, RoundedCornerShape(999.dp))
                    .border(1.dp, if (active) AppTheme.Primary else AppTheme.Border, RoundedCornerShape(999.dp))
                    .clickable(enabled = enabled) { onSelect(value) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** A muted, outlined counterpart to [AppButton] for the two "continue without an answer" actions. */
@Composable
private fun SecondaryAction(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = AppTheme.Label,
        color = if (enabled) AppTheme.Foreground else AppTheme.Disabled,
        modifier = modifier
            .clip(RoundedCornerShape(AppTheme.Radius))
            .border(1.dp, AppTheme.Border, RoundedCornerShape(AppTheme.Radius))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
    )
}

/**
 * A question that is no longer open, as it appears in a run's record: what was asked, what was
 * decided and by whom. Read-only by construction — the server refuses a second answer.
 */
@Composable
internal fun AnsweredInteractionRow(interaction: InteractionDto) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AppTheme.Border, RoundedCornerShape(AppTheme.Radius))
            .background(AppTheme.Background, RoundedCornerShape(AppTheme.Radius))
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = interactionStatusLabel(interaction), style = AppTheme.Label, color = AppTheme.Muted)
            val stamp = formatTimestamp(interaction.answeredAt ?: interaction.createdAt)
            if (stamp != null) {
                Text(text = stamp, style = AppTheme.Label, color = AppTheme.Muted)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(text = interaction.message, style = AppTheme.Body, color = AppTheme.Foreground)
    }
}

/** How a question ended, in the same vocabulary the dashboard uses. */
internal fun interactionStatusLabel(interaction: InteractionDto): String {
    val who = interaction.answeredByName?.takeIf { it.isNotBlank() }
    return when (interaction.status) {
        "pending" -> "ждёт ответа"
        "answered", "delivered" -> when (interaction.responseAction) {
            "accept" -> if (who != null) "ответил(а) $who" else "получен ответ"
            "decline" -> "пропущен"
            else -> "отменён"
        }
        "expired" -> "истёк срок ответа"
        "cancelled" -> "отменён"
        "orphaned" -> "запуск прерван"
        else -> interaction.status
    }
}
