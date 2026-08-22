package com.example.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.example.app.components.DownIcon
import com.example.app.components.ForwardIcon
import com.example.app.components.StatusIcon
import com.example.app.data.DiffDto
import com.example.app.data.InteractionDto
import com.example.app.data.LogEntryDto
import com.example.app.data.RunDto
import com.example.app.data.RunInstructionDto
import com.example.app.data.StageDto
import com.example.app.diff.DiffPalette
import com.example.app.diff.DiffViewer
import com.example.app.diff.FileDiff
import com.example.app.diff.UnifiedPatchParser
import com.example.app.json.JsonViewer
import com.example.app.markdown.MarkdownText
import com.example.app.theme.AppTheme
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

/**
 * The task a run belongs to, as the way into it.
 *
 * A run reached from the board, the activity feed or a notification is otherwise a dead end:
 * everything the run itself does not carry — the description the agent was given, the discussion,
 * the other attempts — lives on the task, and the only route there was the project list.
 *
 * [taskTitle] and [projectName] come from the server and are both absent against one that predates
 * them (and in a run cached by an older build). The row is shown regardless: the destination exists
 * either way, and only its label is the poorer for it.
 */
@Composable
internal fun TaskLinkRow(taskTitle: String?, projectName: String?, onClick: () -> Unit) {
    val title = taskTitle?.takeIf { it.isNotBlank() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.Radius))
            .clickable(role = Role.Button, onClick = onClick)
            .border(1.dp, AppTheme.Border, RoundedCornerShape(AppTheme.Radius))
            .background(AppTheme.Surface, RoundedCornerShape(AppTheme.Radius))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            val context = listOfNotNull(
                "Задача",
                projectName?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            Text(text = context, style = AppTheme.Label, color = AppTheme.Muted)
            Spacer(Modifier.height(2.dp))
            Text(
                text = title ?: "Открыть задачу",
                style = AppTheme.Body,
                color = AppTheme.Primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ForwardIcon(AppTheme.Muted, size = 14.dp)
    }
}

/**
 * What the run was asked to do, above what it answered.
 *
 * A run's page used to open with the task's *title* and go straight to the agent's output, which
 * for a task called "выполни" left no way to tell what the run was about at all. The server
 * resolves the instruction to the comment the run was triggered from, or to the task's description
 * when there was no such comment ([RunInstructionDto.source] says which), so this block is the
 * question the whole page is an answer to.
 *
 * Folded past a few lines: an instruction can be a page of prose, and pushing the result off the
 * screen to show it in full would trade one unreadable page for another.
 */
@Composable
internal fun InstructionCard(instruction: RunInstructionDto) {
    var expanded by remember(instruction.body) { mutableStateOf(false) }
    val long = instruction.body.length > INSTRUCTION_FOLD_CHARS

    SectionTitle("Что просили")
    Spacer(Modifier.height(8.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AppTheme.Border, RoundedCornerShape(AppTheme.Radius))
            .background(AppTheme.Background, RoundedCornerShape(AppTheme.Radius))
            .padding(16.dp),
    ) {
        val origin = listOfNotNull(
            if (instruction.source == "comment") "из комментария" else "из описания задачи",
            instruction.authorName?.takeIf { it.isNotBlank() },
            formatTimestamp(instruction.createdAt),
        ).joinToString(" · ")
        Text(text = origin, style = AppTheme.Footnote, color = AppTheme.Muted)
        Spacer(Modifier.height(8.dp))
        val shown = if (long && !expanded) instruction.body.take(INSTRUCTION_FOLD_CHARS).trimEnd() + "…" else instruction.body
        MarkdownText(text = shown, style = AppTheme.Body, color = AppTheme.Foreground)
        if (long) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (expanded) "Свернуть" else "Показать полностью",
                style = AppTheme.Footnote,
                color = AppTheme.Accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .padding(vertical = 2.dp),
            )
        }
    }
}

/** Where an instruction is cut when folded — a few paragraphs, not a screenful. */
private const val INSTRUCTION_FOLD_CHARS = 400

/**
 * The full record of one pipeline run: its stages, its log trace and the worker's final answer.
 *
 * The page is ordered by how much a reader needs each part: what happened (status, cost, a
 * failure's reason), then what is being asked of them (a pending question), then what the agent
 * actually wrote, and only then the machinery — the log and the raw worker payload, both folded.
 * The trace used to sit in the middle of the page, between the stages and their summary, which put
 * a scrollbox of debug lines between a person and the two paragraphs they came to read.
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
    // Which folded sections the reader has opened. Keyed by the run, so opening the log on one run
    // does not open it on the next one shown in the same slot.
    val opened = remember(run.id) { mutableStateMapOf<String, Boolean>() }

    SectionTitle("Результат запуска")
    Spacer(Modifier.height(12.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AppTheme.Border, RoundedCornerShape(AppTheme.Radius))
            .background(AppTheme.Surface, RoundedCornerShape(AppTheme.Radius))
            .padding(16.dp),
    ) {
        // No status badge here: the page heads itself with a fact strip that already says
        // «Статус», and the two sat one under the other. The spend stays, because it is the
        // breakdown that strip has no room for — the in/out/cache split and the price estimate.
        run.usage?.takeIf { totalTokens(it) > 0 }?.let { usage ->
            val parts = buildList {
                add("${formatTokens(totalTokens(usage))} токенов")
                add("вход ${formatTokens(usage.inputTokens)}")
                add("выход ${formatTokens(usage.outputTokens)}")
                add("кэш ${formatTokens(usage.cacheReadTokens + usage.cacheWriteTokens)}")
                usage.estimatedCostUsd?.takeIf { it > 0 }?.let { add(formatCostUsd(it)) }
            }
            Text(text = parts.joinToString(" · "), style = AppTheme.Label, color = AppTheme.Muted)
        }

        // Directly under the strip that says "ошибка", not at the foot of the page: the reason is
        // the first thing wanted from a failed run, and it used to be below every stage and log.
        run.errorMessage?.takeIf { it.isNotBlank() }?.let { failure ->
            if (run.usage != null) Spacer(Modifier.height(8.dp))
            Text(text = failure, style = AppTheme.Body, color = AppTheme.Danger)
        }

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

        if (run.stages.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SectionTitle("Этапы")
            run.stages.forEach { stage ->
                Spacer(Modifier.height(8.dp))
                StageCard(stage)
            }
        }

        // The worker builds this by gluing the stages' own text together, so on the common
        // single-stage pipeline it is the paragraph directly above it, once more. Shown only when
        // it says something the stages did not — never dropped when there is any doubt, because on
        // a run whose stages left no prose it is the only account of what happened.
        val summary = run.resultSummary?.takeIf { it.isNotBlank() }
        if (summary != null && !summaryRepeatsStages(summary, run.stages)) {
            Spacer(Modifier.height(16.dp))
            SectionTitle("Итог воркера")
            Spacer(Modifier.height(8.dp))
            MarkdownText(text = summary, style = AppTheme.Body, color = AppTheme.Foreground)
        }

        run.diff?.let { diff ->
            Spacer(Modifier.height(16.dp))
            DiffSection(diff)
        }

        if (run.logs.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            val open = opened[LOG_SECTION] == true
            CollapsibleSection(
                title = "Лог выполнения",
                note = logCountLabel(run.logs.size),
                expanded = open,
                onToggle = { opened[LOG_SECTION] = !open },
            ) {
                LogTrace(logs = run.logs)
            }
            // Folded, a running run would show no sign of life at all — the log is the only thing
            // moving while a stage works. Its newest line stays out, and only while that is true.
            if (!open && run.status in ACTIVE_RUN_STATES) {
                Spacer(Modifier.height(6.dp))
                LogLine(run.logs.last())
            }
        }

        run.workerResult?.let { result ->
            Spacer(Modifier.height(16.dp))
            val open = opened[WORKER_RESULT_SECTION] == true
            CollapsibleSection(
                title = "Полный ответ воркера",
                note = "JSON",
                expanded = open,
                onToggle = { opened[WORKER_RESULT_SECTION] = !open },
            ) {
                // Worker result schemas may evolve independently. Show the complete JSON the server
                // persisted rather than silently dropping fields the client does not know yet — as a
                // tree, because "complete" here regularly means hundreds of lines.
                JsonViewer(element = result, rootLabel = "результат")
            }
        }
    }
}

private const val LOG_SECTION = "logs"
private const val WORKER_RESULT_SECTION = "workerResult"

/**
 * Whether «Итог воркера» would only repeat the stages above it.
 *
 * The worker's summary is `"- " + each stage's text`, joined by newlines
 * (`worker/src/agentiz_worker/main.py`), so the two are the same words in the same order and differ
 * only by that bullet and by where the lines break. Compared with both removed: anything the worker
 * added of its own — a line no stage produced, a summary written instead of the stages' text —
 * leaves a difference and the section is shown.
 */
internal fun summaryRepeatsStages(summary: String, stages: List<StageDto>): Boolean {
    val responses = stages.mapNotNull { agentResponse(it) }
    if (responses.isEmpty()) return false
    return skeleton(summary) == skeleton(responses.joinToString(""))
}

/** The text with everything the joining could have changed taken out: bullets and whitespace. */
private fun skeleton(text: String): String =
    text.filterNot { it.isWhitespace() || it == '-' || it == '*' || it == '•' }

/** What the agent said at the end of a stage, if it said anything. */
internal fun agentResponse(stage: StageDto): String? = (stage.output as? JsonObject)
    ?.get("agentResponse")
    ?.jsonPrimitive
    ?.contentOrNull
    ?.takeIf { it.isNotBlank() }

/** "3 строки" — a count a person reads, so the folded log says how much is behind it. */
internal fun logCountLabel(count: Int): String {
    val word = when {
        count % 100 in 11..14 -> "строк"
        count % 10 == 1 -> "строка"
        count % 10 in 2..4 -> "строки"
        else -> "строк"
    }
    return "$count $word"
}

/**
 * A section that is a header until it is asked for: the log and the raw worker payload are both
 * reference material, and both are long enough to be the whole screen when they are not.
 */
@Composable
private fun CollapsibleSection(
    title: String,
    note: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppTheme.Radius))
                .clickable(role = Role.Button, onClick = onToggle)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (expanded) DownIcon(AppTheme.Muted, size = 14.dp) else ForwardIcon(AppTheme.Muted, size = 14.dp)
            Spacer(Modifier.width(8.dp))
            SectionTitle(title)
            if (note != null) {
                Spacer(Modifier.width(6.dp))
                Text(text = "· $note", style = AppTheme.Label, color = AppTheme.Disabled)
            }
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            content()
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

/**
 * One stage: who ran it, how it ended, what it spent — and, in full, what the agent wrote.
 *
 * Boxed rather than laid straight into the page as it was: a pipeline of three stages used to run
 * together into one column of prose with a status word floating in it, and the agent's own text is
 * the part of this page most worth reading, so it gets a frame of its own.
 */
@Composable
private fun StageCard(stage: StageDto) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.Radius))
            .border(1.dp, AppTheme.Border, RoundedCornerShape(AppTheme.Radius))
            .background(AppTheme.Background, RoundedCornerShape(AppTheme.Radius))
            .padding(12.dp),
    ) {
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

        // The stage's own spend (last attempt) and the model it actually ran on — the dashboard
        // shows the same pair beside each stage, from the same `output.usage` block.
        val usage = remember(stage.output) { stageUsage(stage.output) }
        if (usage != null && totalTokens(usage) > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = listOfNotNull("${formatTokens(totalTokens(usage))} ткн", usage.model).joinToString(" · "),
                style = AppTheme.Label,
                color = AppTheme.Muted,
            )
        }

        stage.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
            Spacer(Modifier.height(6.dp))
            Text(text = message, style = AppTheme.Label, color = AppTheme.Danger)
        }

        val response = remember(stage.output) { agentResponse(stage) }
        if (response != null) {
            Spacer(Modifier.height(8.dp))
            // What the agent said at the end of the stage — its whole report, Markdown and all.
            MarkdownText(text = response, style = AppTheme.Body, color = AppTheme.Foreground)
        } else if (stage.output != null) {
            Spacer(Modifier.height(8.dp))
            // No prose from the agent — whatever the stage did leave behind, foldable.
            JsonViewer(element = stage.output, rootLabel = "вывод этапа")
        }
    }
}

/**
 * The run's process trace: a level filter over the lines, and the lines themselves.
 *
 * Which level is being read is deliberately *not* keyed to the run and not remembered anywhere
 * else — it is a way of looking at the page currently open, and a filter still in force when the
 * next run is opened would hide lines nobody asked to hide.
 */
@Composable
private fun LogTrace(logs: List<LogEntryDto>) {
    var picked by remember { mutableStateOf<String?>(null) }
    val levels = remember(logs) { logLevelCounts(logs) }
    // A level the trace no longer contains cannot stay selected: the log grows while a run is
    // live, and an early page of it may carry levels the tail does not.
    val selected = picked?.takeIf { level -> levels.any { it.first == level } }
    val shown = remember(logs, selected) {
        if (selected == null) logs else logs.filter { it.level == selected }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Only the levels this run actually emitted, so the filter never offers a choice that
        // empties the box. A single-level trace gets no filter at all — there is nothing to pick.
        if (levels.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LogLevelChip(
                    label = "все",
                    count = logs.size,
                    color = AppTheme.Muted,
                    selected = selected == null,
                    onClick = { picked = null },
                )
                levels.forEach { (level, count) ->
                    LogLevelChip(
                        label = logLevelLabel(level),
                        count = count,
                        color = logLevelColor(level),
                        selected = selected == level,
                        // Tapping the active level returns to "все": the way back has to be where
                        // the way in was, not only on a chip at the far left of a scrolled row.
                        onClick = { picked = if (selected == level) null else level },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Keep the process trace selectable: workers can emit details that need to be
        // copied into an issue or a reply. SelectionContainer provides the native copy
        // action on touch devices and Cmd/Ctrl+C support on desktop.
        SelectionContainer {
            Column(
                // A trace of a few hundred debug lines would otherwise push the rest of the
                // page out of reach. Capped and given its own scroll, the log stays
                // inspectable without becoming the whole page; heightIn means a short log
                // still shrinks.
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                shown.forEach { LogLine(it) }
            }
        }
    }
}

/** Log levels, most severe first — the order the filter offers them in. */
private val LOG_LEVEL_ORDER = listOf("error", "warn", "info", "debug")

/**
 * The levels present in a trace with how many lines each has, severe first; anything the server
 * starts emitting that this build does not know about follows, alphabetically, rather than being
 * silently unfilterable.
 */
internal fun logLevelCounts(logs: List<LogEntryDto>): List<Pair<String, Int>> {
    val counts = logs.groupingBy { it.level }.eachCount()
    val known = LOG_LEVEL_ORDER.filter { counts.containsKey(it) }
    val rest = counts.keys.filterNot { it in LOG_LEVEL_ORDER }.sorted()
    return (known + rest).map { level -> level to (counts[level] ?: 0) }
}

/** The level as a person reads it; an unknown level keeps whatever the server called it. */
internal fun logLevelLabel(level: String): String = when (level) {
    "error" -> "ошибки"
    "warn" -> "предупреждения"
    "info" -> "инфо"
    "debug" -> "отладка"
    else -> level
}

/** The colour a level wears — both as the line's dot and as its chip. */
private fun logLevelColor(level: String): Color = when (level) {
    "error" -> AppTheme.Danger
    "warn" -> AppTheme.Warning
    "debug" -> AppTheme.Disabled
    else -> AppTheme.Muted
}

/** One level of the filter, worn like the policy chips: outlined when off, filled when it is on. */
@Composable
private fun LogLevelChip(
    label: String,
    count: Int,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .background(if (selected) AppTheme.Primary else AppTheme.Surface, RoundedCornerShape(999.dp))
            .border(1.dp, AppTheme.Border, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        LevelDot(if (selected) AppTheme.PrimaryForeground else color)
        Text(
            text = "$label · $count",
            style = AppTheme.Footnote,
            color = if (selected) AppTheme.PrimaryForeground else AppTheme.Muted,
        )
    }
}

/** The level of a line, as a disc — the word it replaces was a third of the width of a phone. */
@Composable
private fun LevelDot(color: Color) {
    Box(
        modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(color, CircleShape),
    )
}

/**
 * One line of the run's process trace — every level, so the "thinking" behind a run is visible.
 *
 * The line is the message and nothing else: the `[debug]` prefix it used to carry cost a third of
 * the width on a phone and told the reader what the coloured disc now does. When it happened and
 * which stage produced it open under the line on a tap — that is per-line detail nobody needs on
 * every line at once, and printing it inline is what made the trace unreadable.
 */
@Composable
private fun LogLine(log: LogEntryDto) {
    var detailed by remember(log) { mutableStateOf(false) }
    val color = when (log.level) {
        "error" -> AppTheme.Danger
        "warn", "debug" -> AppTheme.Muted
        else -> AppTheme.Foreground
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClick = { detailed = !detailed })
            .background(if (detailed) AppTheme.Background else Color.Transparent, RoundedCornerShape(6.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            // The disc sits on the first line of a message that wraps, not in the middle of it.
            Box(modifier = Modifier.padding(top = 6.dp, end = 8.dp)) { LevelDot(logLevelColor(log.level)) }
            Text(text = log.message, style = AppTheme.Label, color = color)
        }
        if (detailed) {
            Text(
                text = logLineDetail(log),
                style = AppTheme.Footnote,
                color = AppTheme.Muted,
                modifier = Modifier.padding(start = 15.dp, top = 2.dp),
            )
        }
    }
}

/**
 * What a tapped line says about itself: when it was written, at what level, and — on a pipeline of
 * more than one stage — which stage wrote it. A line whose timestamp the server did not send (an
 * older build, a run cached before the field existed) still names its level rather than opening on
 * an empty hint.
 */
internal fun logLineDetail(log: LogEntryDto): String = listOfNotNull(
    formatLogTimestamp(log.createdAt),
    logLevelLabel(log.level),
    log.stageRole?.takeIf { it.isNotBlank() },
).joinToString(" · ")

/** The word and colour a run's status wears, shared by the badge and the detail page's fact grid. */
internal fun runStatusPresentation(status: String): Pair<String, Color> = when (status) {
    "pending" -> "в очереди" to AppTheme.Muted
    "running" -> "выполняется" to AppTheme.Warning
    // The one non-terminal state that needs a person: coloured, unlike the other in-flight ones.
    "waiting_input" -> "ждёт ответа" to AppTheme.Accent
    "succeeded" -> "успешно" to AppTheme.Success
    "failed" -> "ошибка" to AppTheme.Danger
    "cancelled" -> "отменён" to AppTheme.Disabled
    else -> status to AppTheme.Muted
}

/**
 * A run's status the way GitHub's workflow list wears it: the state disc plus a word in the same
 * colour, no filled pill — the disc is the signal and the word only spells it out.
 */
@Composable
internal fun RunStatusBadge(status: String) {
    val (label, color) = runStatusPresentation(status)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        StatusIcon(status, size = 15.dp)
        Text(text = label, style = AppTheme.Label, color = color)
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text = text, style = AppTheme.Label, color = AppTheme.Muted)
}

