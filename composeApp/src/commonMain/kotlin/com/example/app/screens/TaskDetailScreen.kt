package com.example.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.example.app.components.AppButton
import com.example.app.components.AppTextField
import com.example.app.data.AgentizApi
import com.example.app.data.ApiException
import com.example.app.data.CommentDto
import com.example.app.data.LogEntryDto
import com.example.app.data.RunDto
import com.example.app.data.Session
import com.example.app.data.StageDto
import com.example.app.data.TaskDetailDto
import com.example.app.data.TaskDto
import com.example.app.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Pipeline states that mean the worker is still holding the task. */
private val ACTIVE_TASK_STATES = setOf("queued", "running")

/**
 * One task: what it is, what its last pipeline run concluded, and the discussion around it.
 *
 * A run is executed by a worker out of band, so "Запустить" only enqueues it. While the task sits
 * in an active state the screen re-polls on a timer — that is what turns the queued run into a
 * visible result and the agent's report into a new comment without the reader doing anything.
 */
@Composable
fun TaskDetailScreen(
    session: Session,
    taskId: String,
    onBack: () -> Unit,
) {
    val api = remember(session.serverUrl) { AgentizApi(session.serverUrl) }
    DisposableEffect(api) { onDispose { api.close() } }
    val scope = rememberCoroutineScope()

    var detail by remember { mutableStateOf<TaskDetailDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var comment by remember { mutableStateOf("") }

    suspend fun load() {
        try {
            detail = api.task(session.token, taskId)
            error = null
        } catch (e: ApiException) {
            error = e.message
        } catch (e: Throwable) {
            error = "Ошибка сети: ${e.message ?: "неизвестная ошибка"}"
        }
    }

    LaunchedEffect(taskId, reloadKey) { load() }

    // Poll only while something is actually in flight; a finished task costs no requests.
    val active = detail?.task?.status in ACTIVE_TASK_STATES
    LaunchedEffect(taskId, active) {
        while (active) {
            delay(2000)
            load()
        }
    }

    fun runPipeline() {
        if (busy) return
        busy = true
        scope.launch {
            try {
                api.runTask(session.token, taskId)
                load()
            } catch (e: ApiException) {
                error = e.message
            } catch (e: Throwable) {
                error = "Ошибка сети: ${e.message ?: "неизвестная ошибка"}"
            } finally {
                busy = false
            }
        }
    }

    fun submitComment() {
        if (busy || comment.isBlank()) return
        busy = true
        scope.launch {
            try {
                api.addComment(session.token, taskId, comment.trim())
                comment = ""
                load()
            } catch (e: ApiException) {
                error = e.message
            } catch (e: Throwable) {
                error = "Ошибка сети: ${e.message ?: "неизвестная ошибка"}"
            } finally {
                busy = false
            }
        }
    }

    val current = detail
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.Background)
            .padding(24.dp),
    ) {
        ScreenHeader(
            title = current?.task?.title ?: "Задача",
            subtitle = current?.task?.externalId?.takeIf { it.isNotBlank() },
            onBack = onBack,
        )
        Spacer(Modifier.height(20.dp))

        when {
            current == null && error != null -> RetryState(message = error!!, onRetry = { reloadKey++ })
            current == null -> CenterMessage("Загрузка задачи…")
            else -> Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                TaskSummary(current.task)

                if (error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(text = error!!, style = AppTheme.Label, color = AppTheme.Danger)
                }

                Spacer(Modifier.height(16.dp))
                AppButton(
                    text = when {
                        busy -> "…"
                        current.task.status in ACTIVE_TASK_STATES -> "Выполняется…"
                        current.latestRun == null -> "Запустить пайплайн"
                        else -> "Запустить ещё раз"
                    },
                    onClick = ::runPipeline,
                    enabled = !busy && current.task.status !in ACTIVE_TASK_STATES,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (current.latestRun != null) {
                    Spacer(Modifier.height(20.dp))
                    RunResult(current.latestRun)
                }

                Spacer(Modifier.height(24.dp))
                SectionTitle("Обсуждение")
                Spacer(Modifier.height(12.dp))
                if (current.comments.isEmpty()) {
                    Text(text = "Пока нет комментариев.", style = AppTheme.Body, color = AppTheme.Muted)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        current.comments.forEach { CommentCard(it) }
                    }
                }

                Spacer(Modifier.height(20.dp))
                AppTextField(
                    label = "Новый комментарий",
                    value = comment,
                    onValueChange = { comment = it },
                    placeholder = "Написать…",
                    enabled = !busy,
                    imeAction = ImeAction.Done,
                )
                Spacer(Modifier.height(12.dp))
                AppButton(
                    text = "Отправить",
                    onClick = ::submitComment,
                    enabled = !busy && comment.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun TaskSummary(task: TaskDto) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AppTheme.Border, RoundedCornerShape(AppTheme.Radius))
            .background(AppTheme.Surface, RoundedCornerShape(AppTheme.Radius))
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TaskStatusBadge(task.status)
            Text(
                text = if (task.runCount == 1) "1 запуск" else "${task.runCount} запусков",
                style = AppTheme.Label,
                color = AppTheme.Muted,
            )
        }
        val description = task.description?.takeIf { it.isNotBlank() }
        if (description != null) {
            Spacer(Modifier.height(12.dp))
            Text(text = description, style = AppTheme.Body, color = AppTheme.Foreground)
        }
        if (task.tags.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(text = task.tags.joinToString(" · "), style = AppTheme.Label, color = AppTheme.Muted)
        }
    }
}

@Composable
private fun RunResult(run: RunDto) {
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

        run.stages.forEach { stage ->
            Spacer(Modifier.height(12.dp))
            StageRow(stage)
        }

        if (run.logs.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SectionTitle("Лог выполнения")
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                run.logs.forEach { LogLine(it) }
            }
        }

        val summary = run.resultSummary?.takeIf { it.isNotBlank() }
        if (summary != null) {
            Spacer(Modifier.height(16.dp))
            Text(text = summary, style = AppTheme.Body, color = AppTheme.Foreground)
        }
        val failure = run.errorMessage?.takeIf { it.isNotBlank() }
        if (failure != null) {
            Spacer(Modifier.height(12.dp))
            Text(text = failure, style = AppTheme.Body, color = AppTheme.Danger)
        }
    }
}

@Composable
private fun StageRow(stage: StageDto) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                "succeeded" -> "готово"
                "failed" -> "ошибка"
                "skipped" -> "пропущено"
                else -> stage.status
            },
            style = AppTheme.Label,
            color = if (stage.status == "failed") AppTheme.Danger else AppTheme.Muted,
        )
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
private fun RunStatusBadge(status: String) {
    val (label, color) = when (status) {
        "pending" -> "в очереди" to AppTheme.Muted
        "running" -> "выполняется" to AppTheme.Muted
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
private fun CommentCard(comment: CommentDto) {
    // The author kind is the one thing a reader scans for, so it gets the accent colour rather
    // than the body text: agent reports and human replies must not look alike.
    val (kindLabel, kindColor) = when (comment.authorKind) {
        "agent" -> "агент" to AppTheme.Primary
        "system" -> "система" to AppTheme.Muted
        else -> "человек" to AppTheme.Foreground
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AppTheme.Border, RoundedCornerShape(AppTheme.Radius))
            .background(AppTheme.Background, RoundedCornerShape(AppTheme.Radius))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = kindLabel, style = AppTheme.Label, color = kindColor)
            val author = comment.authorName?.takeIf { it.isNotBlank() }
            if (author != null) {
                Text(text = " · $author", style = AppTheme.Label, color = AppTheme.Muted)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(text = comment.body, style = AppTheme.Body, color = AppTheme.Foreground)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = AppTheme.Label, color = AppTheme.Muted)
}
