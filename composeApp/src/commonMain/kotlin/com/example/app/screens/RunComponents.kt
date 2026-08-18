package com.example.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.example.app.components.DownIcon
import com.example.app.components.ForwardIcon
import com.example.app.data.DiffDto
import com.example.app.data.InteractionDto
import com.example.app.data.LogEntryDto
import com.example.app.data.RunDto
import com.example.app.data.StageDto
import com.example.app.diff.DiffPalette
import com.example.app.diff.DiffViewer
import com.example.app.diff.FileDiff
import com.example.app.diff.UnifiedPatchParser
import com.example.app.theme.AppTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pipeline states a run can still be in — shared between the task screen (which needs to know
 * whether *some* run is active to keep polling and offer "Остановить") and the run's own detail
 * page (which polls the same way while looking at just one).
 *
 * `waiting_input` belongs here even though nothing is executing: the run is paused on a question
 * and resumes the moment it is answered, so treating it as finished would stop polling exactly when
 * the screen most needs to keep refreshing.
 */
internal val ACTIVE_RUN_STATES = setOf("pending", "running", "waiting_input")

internal val prettyJson = Json { prettyPrint = true }

/**
 * The full record of one pipeline run: its stages, its log trace and the worker's final answer.
 *
 * [onAnswerInteraction] is what makes a paused run actionable from its own page — without it the
 * questions are still shown, but read-only, which is all a finished run's history needs.
 */
@Composable
internal fun RunResult(
    run: RunDto,
    interactionBusyId: String? = null,
    onAnswerInteraction: ((InteractionDto, String, JsonObject?) -> Unit)? = null,
) {
    SectionTitle("Результат запуска")
    Spacer(Modifier.height(12.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AppTheme.Border, RoundedCornerShape(AppTheme.Radius))
            .background(AppTheme.Surface, RoundedCornerShape(AppTheme.Radius))
            .padding(16.dp),
    ) {
        RunStatusBadge(run.status)

        if (run.interactions.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SectionTitle("Вопросы агента")
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                run.interactions.forEach { interaction ->
                    if (interaction.status == "pending" && onAnswerInteraction != null) {
                        InteractionCard(
                            interaction = interaction,
                            busy = interactionBusyId == interaction.id,
                            onAnswer = { action, content -> onAnswerInteraction(interaction, action, content) },
                        )
                    } else {
                        AnsweredInteractionRow(interaction)
                    }
                }
            }
        }

        run.stages.forEach { stage ->
            Spacer(Modifier.height(12.dp))
            StageRow(stage)
        }

        if (run.logs.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SectionTitle("Лог выполнения")
            Spacer(Modifier.height(8.dp))
            // Keep the process trace selectable: workers can emit details that need to be copied
            // into an issue or a reply. SelectionContainer provides the native copy action on
            // touch devices and Cmd/Ctrl+C support on desktop.
            SelectionContainer {
                Column(
                    // A trace of a few hundred debug lines would otherwise push the rest of the page
                    // out of reach. Capped and given its own scroll, the log stays inspectable
                    // without becoming the whole page; heightIn means a short log still shrinks.
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    run.logs.forEach { LogLine(it) }
                }
            }
        }

        run.diff?.let { diff ->
            Spacer(Modifier.height(16.dp))
            DiffSection(diff)
        }

        val summary = run.resultSummary?.takeIf { it.isNotBlank() }
        if (summary != null) {
            Spacer(Modifier.height(16.dp))
            SectionTitle("Итог воркера")
            Spacer(Modifier.height(8.dp))
            Text(text = summary, style = AppTheme.Body, color = AppTheme.Foreground)
        }

        run.workerResult?.let { result ->
            Spacer(Modifier.height(16.dp))
            SectionTitle("Полный ответ воркера")
            Spacer(Modifier.height(8.dp))
            // Worker result schemas may evolve independently. Display the complete JSON that the
            // server persisted rather than silently dropping fields the client does not know yet.
            SelectionContainer {
                Text(
                    text = prettyJson.encodeToString(result),
                    style = AppTheme.Label,
                    color = AppTheme.Foreground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        }
        val failure = run.errorMessage?.takeIf { it.isNotBlank() }
        if (failure != null) {
            Spacer(Modifier.height(12.dp))
            Text(text = failure, style = AppTheme.Body, color = AppTheme.Danger)
        }
    }
}

/** The amber the dashboard uses for its "patch truncated" warning; not a theme token yet. */
private val WarningText = Color(0xFFB45309)

/**
 * «Изменения» — what the run did to the code, rendered from the same unified patch the dashboard
 * shows. Wording and badge order follow `AgentizRunDetail.tsx` so the two clients read alike;
 * what the web has and this deliberately does not: apply/approve buttons (a separate feature with
 * its own rights) and the unified/split switch (split needs a wide screen).
 */
@Composable
internal fun DiffSection(diff: DiffDto) {
    // Parsed once per patch, not per recomposition — a live run polls every two seconds.
    val files = remember(diff.patch) {
        diff.patch?.let { UnifiedPatchParser.parse(it) } ?: emptyList()
    }
    // Files a reader has toggled; anything untouched falls back to "only the first is open", so
    // a ten-file patch does not unroll into one endless page.
    val toggled = remember(diff.patch) { mutableStateMapOf<Int, Boolean>() }

    SectionTitle("Изменения")
    Spacer(Modifier.height(8.dp))

    val stats = diff.stats
    val fileCount = stats?.files ?: files.size
    val insertions = stats?.insertions ?: files.sumOf { it.additions }
    val deletions = stats?.deletions ?: files.sumOf { it.deletions }
    Text(
        text = buildAnnotatedString {
            append("от ${diff.baseSha?.take(12) ?: "—"} · $fileCount файл(ов), ")
            withStyle(SpanStyle(color = DiffPalette.Light.addedText)) { append("+$insertions") }
            append(" ")
            withStyle(SpanStyle(color = DiffPalette.Light.deletedText)) { append("−$deletions") }
            val appliedAt = formatTimestamp(diff.appliedAt)
            append(
                if (appliedAt != null) {
                    " · применено $appliedAt, коммит ${diff.appliedCommitSha?.take(12) ?: ""}"
                } else {
                    " · в репозиторий не отправлено"
                },
            )
        },
        style = AppTheme.Label,
        color = AppTheme.Muted,
    )

    if (diff.truncated) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Патч обрезан по лимиту размера — показана часть изменений.",
            style = AppTheme.Label,
            color = WarningText,
        )
    }

    if (files.isEmpty() && diff.patch != null) {
        Spacer(Modifier.height(6.dp))
        Text(text = "Дифф пуст.", style = AppTheme.Label, color = AppTheme.Muted)
    }

    files.forEachIndexed { index, file ->
        Spacer(Modifier.height(8.dp))
        FileDiffCard(
            file = file,
            expanded = toggled[index] ?: (index == 0),
            onToggle = { toggled[index] = !(toggled[index] ?: (index == 0)) },
        )
    }
}

/** One file of the patch: a header that folds the body, so a long patch stays a list of names. */
@Composable
private fun FileDiffCard(file: FileDiff, expanded: Boolean, onToggle: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.Radius))
            .border(1.dp, AppTheme.Border, RoundedCornerShape(AppTheme.Radius))
            .background(AppTheme.Background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .background(AppTheme.Surface)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (expanded) DownIcon(AppTheme.Muted, size = 14.dp) else ForwardIcon(AppTheme.Muted, size = 14.dp)
            Text(
                text = file.path,
                style = AppTheme.Label,
                color = AppTheme.Foreground,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text(text = "+${file.additions}", style = AppTheme.Label, color = DiffPalette.Light.addedText)
            Spacer(Modifier.width(6.dp))
            Text(text = "−${file.deletions}", style = AppTheme.Label, color = DiffPalette.Light.deletedText)
        }
        if (expanded) {
            SelectionContainer {
                DiffViewer(file, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

@Composable
private fun StageRow(stage: StageDto) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stage.role,
                style = AppTheme.Label,
                color = AppTheme.Foreground,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            Text(
                text = when (stage.status) {
                    "pending" -> "ждёт"
                    "running" -> "идёт"
                    "waiting_input" -> "ждёт ответа"
                    "succeeded" -> "готово"
                    "failed" -> "ошибка"
                    "skipped" -> "пропущено"
                    else -> stage.status
                },
                style = AppTheme.Label,
                color = if (stage.status == "failed") AppTheme.Danger else AppTheme.Muted,
            )
        }

        val response = (stage.output as? JsonObject)
            ?.get("agentResponse")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }
        if (response != null) {
            Spacer(Modifier.height(6.dp))
            Text(text = response, style = AppTheme.Body, color = AppTheme.Foreground)
        } else if (stage.output != null) {
            Spacer(Modifier.height(6.dp))
            SelectionContainer {
                Text(
                    text = prettyJson.encodeToString(stage.output),
                    style = AppTheme.Label,
                    color = AppTheme.Muted,
                )
            }
        }
    }
}

/** One line of the run's process trace — every level, so the "thinking" behind a run is visible. */
@Composable
private fun LogLine(log: LogEntryDto) {
    val color = when (log.level) {
        "error" -> AppTheme.Danger
        "warn" -> AppTheme.Muted
        "debug" -> AppTheme.Muted
        else -> AppTheme.Foreground
    }
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = "[${log.level}]",
            style = AppTheme.Label,
            color = color,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = if (log.stageRole != null) "${log.stageRole}: ${log.message}" else log.message,
            style = AppTheme.Label,
            color = color,
        )
    }
}

@Composable
internal fun RunStatusBadge(status: String) {
    val (label, color) = when (status) {
        "pending" -> "в очереди" to AppTheme.Muted
        "running" -> "выполняется" to AppTheme.Muted
        // The one non-terminal state that needs a person: coloured, unlike the other in-flight ones.
        "waiting_input" -> "ждёт ответа" to AppTheme.Primary
        "succeeded" -> "успешно" to AppTheme.Primary
        "failed" -> "ошибка" to AppTheme.Danger
        "cancelled" -> "отменён" to AppTheme.Disabled
        else -> status to AppTheme.Muted
    }
    Text(
        text = label,
        style = AppTheme.Label,
        color = AppTheme.PrimaryForeground,
        modifier = Modifier
            .background(color, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text = text, style = AppTheme.Label, color = AppTheme.Muted)
}

