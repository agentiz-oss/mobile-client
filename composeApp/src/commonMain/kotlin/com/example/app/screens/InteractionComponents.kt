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

/** One property of `requestedSchema.properties`, reduced to what the renderer needs. */
internal data class InteractionField(
    val name: String,
    val title: String,
    val description: String?,
    val kind: FieldKind,
    val options: List<String>,
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

/** The form to render for this question, in the order the schema declares its properties. */
internal fun InteractionDto.formFields(): List<InteractionField> {
    val properties = requestedSchema["properties"] as? JsonObject ?: return emptyList()
    val required = (requestedSchema["required"] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.content }
        ?.toSet()
        ?: emptySet()

    return properties.map { (name, raw) ->
        val definition = raw as? JsonObject ?: JsonObject(emptyMap())
        // `type` may legitimately be a list ("string" or null); only a plain scalar is rendered
        // natively, the union falls through to the raw box.
        val type = definition.stringField("type")
        val options = (definition["enum"] as? JsonArray)?.map { it.asText() } ?: emptyList()
        val kind = when {
            options.isNotEmpty() -> FieldKind.CHOICE
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
            options = options,
            required = name in required,
            initial = when {
                default != null -> default.asText()
                kind == FieldKind.BOOLEAN -> "false"
                else -> ""
            },
        )
    }
}

/**
 * The `content` object for an `accept`. A blank optional field is left out entirely rather than
 * submitted as an empty string: `""` is a value a schema can reject, absence is what "not filled in"
 * actually means.
 */
internal fun buildAnswerContent(fields: List<InteractionField>, values: Map<String, String>): JsonObject =
    buildJsonObject {
        fields.forEach { field ->
            val text = values[field.name] ?: field.initial
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

/** Required fields still empty. Booleans always have a value, so they can never be missing. */
internal fun missingRequired(fields: List<InteractionField>, values: Map<String, String>): List<InteractionField> =
    fields.filter { field ->
        field.required && field.kind != FieldKind.BOOLEAN && (values[field.name] ?: field.initial).isBlank()
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
                enabled = !busy,
                onValueChange = { values[field.name] = it },
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
            Text(text = "Ответ ждут до $deadline", style = AppTheme.Label, color = AppTheme.Muted)
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
    enabled: Boolean,
    onValueChange: (String) -> Unit,
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
            OptionPills(
                options = field.options.map { it to it },
                selected = value,
                enabled = enabled,
                onSelect = onValueChange,
            )
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
