package com.example.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.example.app.components.AppButton
import com.example.app.components.AppTextField
import com.example.app.data.AgentizApi
import com.example.app.data.ApiException
import com.example.app.data.ProjectDto
import com.example.app.data.Session
import com.example.app.data.TaskDto
import com.example.app.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * Tasks of one project: the list, plus an inline form to add one. Creating a task does not start
 * a pipeline — that is a deliberate second step on the task screen, so a half-written task is
 * never executed by accident.
 */
@Composable
fun TasksScreen(
    session: Session,
    project: ProjectDto,
    onOpenTask: (TaskDto) -> Unit,
    onBack: () -> Unit,
) {
    val api = remember(session.serverUrl) { AgentizApi(session.serverUrl) }
    DisposableEffect(api) { onDispose { api.close() } }
    val scope = rememberCoroutineScope()

    var tasks by remember { mutableStateOf<List<TaskDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableStateOf(0) }

    var composing by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(reloadKey) {
        loading = true
        error = null
        try {
            tasks = api.tasks(session.token, project.id)
        } catch (e: ApiException) {
            error = e.message
        } catch (e: Throwable) {
            error = "Ошибка сети: ${e.message ?: "неизвестная ошибка"}"
        } finally {
            loading = false
        }
    }

    fun submit() {
        if (creating || title.isBlank()) return
        creating = true
        error = null
        scope.launch {
            try {
                api.createTask(session.token, project.id, title.trim(), description.trim().ifBlank { null })
                title = ""
                description = ""
                composing = false
                reloadKey++
            } catch (e: ApiException) {
                error = e.message
            } catch (e: Throwable) {
                error = "Ошибка сети: ${e.message ?: "неизвестная ошибка"}"
            } finally {
                creating = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.Background)
            .padding(24.dp),
    ) {
        ScreenHeader(
            title = project.name,
            subtitle = project.slug.takeIf { it.isNotBlank() },
            onBack = onBack,
        )
        Spacer(Modifier.height(20.dp))

        if (composing) {
            NewTaskForm(
                title = title,
                onTitleChange = { title = it },
                description = description,
                onDescriptionChange = { description = it },
                busy = creating,
                onSubmit = ::submit,
                onCancel = {
                    composing = false
                    title = ""
                    description = ""
                },
            )
        } else {
            AppButton(
                text = "Новая задача",
                onClick = { composing = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(20.dp))

        when {
            loading && tasks == null -> CenterMessage("Загрузка задач…")
            error != null && tasks == null -> RetryState(message = error!!, onRetry = { reloadKey++ })
            tasks.isNullOrEmpty() -> CenterMessage("В проекте пока нет задач.")
            else -> {
                // A create error while the list already has content: keep the list, show the reason.
                if (error != null) {
                    Text(text = error!!, style = AppTheme.Label, color = AppTheme.Danger)
                    Spacer(Modifier.height(12.dp))
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(tasks!!, key = { it.id }) { task ->
                        TaskCard(task, onClick = { onOpenTask(task) })
                    }
                }
            }
        }
    }
}

@Composable
private fun NewTaskForm(
    title: String,
    onTitleChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    busy: Boolean,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AppTheme.Border, RoundedCornerShape(AppTheme.Radius))
            .background(AppTheme.Surface, RoundedCornerShape(AppTheme.Radius))
            .padding(16.dp),
    ) {
        AppTextField(
            label = "Заголовок",
            value = title,
            onValueChange = onTitleChange,
            placeholder = "Что нужно сделать",
            enabled = !busy,
        )
        Spacer(Modifier.height(12.dp))
        AppTextField(
            label = "Описание",
            value = description,
            onValueChange = onDescriptionChange,
            placeholder = "Подробности (необязательно)",
            enabled = !busy,
            imeAction = ImeAction.Done,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppButton(
                text = if (busy) "Создание…" else "Создать",
                onClick = onSubmit,
                enabled = !busy && title.isNotBlank(),
                modifier = Modifier.weight(1f),
            )
            AppButton(text = "Отмена", onClick = onCancel, enabled = !busy)
        }
    }
}

@Composable
private fun TaskCard(task: TaskDto, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.Radius))
            .clickable(role = Role.Button, onClick = onClick)
            .border(1.dp, AppTheme.Border, RoundedCornerShape(AppTheme.Radius))
            .background(AppTheme.Surface, RoundedCornerShape(AppTheme.Radius))
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = task.title,
                style = AppTheme.Body,
                color = AppTheme.Foreground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            TaskStatusBadge(task.status)
        }
        if (task.tags.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(text = task.tags.joinToString(" · "), style = AppTheme.Label, color = AppTheme.Muted)
        }
    }
}

/**
 * Pipeline status of a task. Only the terminal-good and terminal-bad states get a strong colour;
 * everything in between stays muted so a list of in-flight tasks does not read as alarming.
 */
@Composable
fun TaskStatusBadge(status: String) {
    val (label, color) = when (status) {
        "new" -> "новая" to AppTheme.Disabled
        "queued" -> "в очереди" to AppTheme.Muted
        "running" -> "выполняется" to AppTheme.Muted
        "waiting_review" -> "на проверке" to AppTheme.Muted
        "done" -> "готово" to AppTheme.Primary
        "failed" -> "ошибка" to AppTheme.Danger
        "cancelled" -> "отменена" to AppTheme.Disabled
        "ignored" -> "пропущена" to AppTheme.Disabled
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

/** Title row with a back affordance, shared by the task list and the task screen. */
@Composable
fun ScreenHeader(title: String, subtitle: String?, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = title,
                style = AppTheme.Subtitle,
                color = AppTheme.Foreground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(text = subtitle, style = AppTheme.Label, color = AppTheme.Muted)
            }
        }
        AppButton(text = "Назад", onClick = onBack)
    }
}

@Composable
fun CenterMessage(text: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = text, style = AppTheme.Body, color = AppTheme.Muted)
    }
}

@Composable
fun RetryState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = message, style = AppTheme.Body, color = AppTheme.Danger)
        Spacer(Modifier.height(16.dp))
        AppButton(text = "Повторить", onClick = onRetry)
    }
}
