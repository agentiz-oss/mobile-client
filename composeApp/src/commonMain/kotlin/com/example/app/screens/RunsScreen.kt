package com.example.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.example.app.components.AppScaffold
import com.example.app.components.MenuEntry
import com.example.app.components.PullToRefresh
import com.example.app.data.AgentizApi
import com.example.app.data.ApiException
import com.example.app.data.RunBoardDto
import com.example.app.data.RunDto
import com.example.app.data.Session
import com.example.app.theme.AppTheme
import kotlinx.coroutines.delay

/** How often the board re-checks. A run's state changes on its own, so this is a watch, not a load. */
private const val RUNS_POLL_MS = 3000L

/**
 * What the agents are doing right now, across every project the user owns — the app's counterpart
 * to the dashboard's "Запуски" page.
 *
 * Everything else in the app reaches a run through its task, which means a run can only be found by
 * someone who already knows which task to open. This screen is the one place that answers "идёт ли
 * что-нибудь вообще", so it keeps polling for as long as it is on screen.
 */
@Composable
fun RunsScreen(
    session: Session,
    menu: List<MenuEntry>,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenRun: (projectId: String, projectName: String?, taskId: String, runId: String) -> Unit,
) {
    val api = remember(session.serverUrl) { AgentizApi(session.serverUrl) }
    DisposableEffect(api) { onDispose { api.close() } }

    var board by remember { mutableStateOf<RunBoardDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }

    suspend fun load() {
        try {
            board = api.runBoard(session.token)
            error = null
        } catch (e: ApiException) {
            error = e.message
        } catch (e: Throwable) {
            error = "Ошибка сети: ${e.message ?: "неизвестная ошибка"}"
        } finally {
            refreshing = false
        }
    }

    LaunchedEffect(reloadKey) { load() }

    LaunchedEffect(Unit) {
        while (true) {
            delay(RUNS_POLL_MS)
            load()
        }
    }

    val current = board
    AppScaffold(
        title = "Запуски",
        subtitle = current?.active?.size?.takeIf { it > 0 }?.let { "$it идёт сейчас" },
        menu = menu,
        onOpenSettings = onOpenSettings,
        onOpenProfile = onOpenProfile,
        onBack = onBack,
    ) {
        when {
            current == null && error != null -> RetryState(message = error!!, onRetry = { reloadKey++ })
            current == null -> CenterMessage("Загрузка запусков…")
            else -> PullToRefresh(
                refreshing = refreshing,
                onRefresh = {
                    refreshing = true
                    reloadKey++
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    error?.let { message ->
                        item(key = "error") {
                            Text(text = message, style = AppTheme.Label, color = AppTheme.Danger)
                        }
                    }

                    item(key = "active-title") { SectionTitle("Идут сейчас (${current.active.size})") }

                    if (current.active.isEmpty()) {
                        item(key = "active-empty") {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Сейчас ничего не выполняется.",
                                    style = AppTheme.Body,
                                    color = AppTheme.Muted,
                                )
                            }
                        }
                    }

                    items(current.active, key = { it.id }) { run ->
                        RunBoardCard(run = run, live = true, onOpenRun = onOpenRun)
                    }

                    if (current.recent.isNotEmpty()) {
                        item(key = "recent-title") { SectionTitle("Завершились недавно") }
                        items(current.recent, key = { it.id }) { run ->
                            RunBoardCard(run = run, live = false, onOpenRun = onOpenRun)
                        }
                    }
                }
            }
        }
    }
}

/**
 * One run as a row of the board. A live run is summarised by where it got to and its newest log
 * line; a finished one by what it concluded — the two are what the reader is asking in each case.
 */
@Composable
private fun RunBoardCard(
    run: RunDto,
    live: Boolean,
    onOpenRun: (projectId: String, projectName: String?, taskId: String, runId: String) -> Unit,
) {
    // A run with no task id cannot be opened — the app loads a run through its task — so such a row
    // stays a readable card rather than a button that would go nowhere.
    val open = run.taskId?.let { taskId ->
        { onOpenRun(run.projectId ?: "", run.projectName, taskId, run.id) }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.Radius))
            .let { base -> if (open != null) base.clickable(role = Role.Button, onClick = open) else base }
            .border(1.dp, AppTheme.Border, RoundedCornerShape(AppTheme.Radius))
            .background(AppTheme.Surface, RoundedCornerShape(AppTheme.Radius))
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = run.taskTitle?.takeIf { it.isNotBlank() } ?: "Запуск",
                style = AppTheme.Subtitle,
                color = AppTheme.Foreground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            RunStatusBadge(run.status)
        }

        val context = listOfNotNull(
            run.projectName?.takeIf { it.isNotBlank() },
            run.trigger.takeIf { it.isNotBlank() },
            formatTimestamp(if (live) run.startedAt ?: run.createdAt else run.finishedAt ?: run.createdAt),
        ).joinToString(" · ")
        if (context.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(text = context, style = AppTheme.Label, color = AppTheme.Muted)
        }

        val progress = stageProgress(run)
        if (progress != null) {
            Spacer(Modifier.height(8.dp))
            Text(text = progress, style = AppTheme.Label, color = AppTheme.Muted)
        }

        if (run.pendingInteractions > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (run.pendingInteractions == 1) "ждёт ответа" else "ждёт ответа (${run.pendingInteractions})",
                style = AppTheme.Label,
                color = AppTheme.PrimaryForeground,
                modifier = Modifier
                    .background(AppTheme.Primary, RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }

        val tail = if (live) {
            run.lastLog?.message?.takeIf { it.isNotBlank() }
        } else {
            run.errorMessage?.takeIf { it.isNotBlank() } ?: run.resultSummary?.takeIf { it.isNotBlank() }
        }
        if (tail != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = tail,
                style = AppTheme.Body,
                color = if (!live && run.errorMessage?.isNotBlank() == true) AppTheme.Danger else AppTheme.Muted,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "2/4 · reviewer идёт" — how far a run got, in one line. Null when the board sent no stages. */
private fun stageProgress(run: RunDto): String? {
    if (run.stages.isEmpty()) return null
    val done = run.stages.count { it.status == "succeeded" || it.status == "skipped" }
    val current = run.stages.firstOrNull { it.status == "running" || it.status == "waiting_input" }
        ?: run.stages.firstOrNull { it.status == "failed" }
    val label = current?.let { stage ->
        val state = when (stage.status) {
            "running" -> "идёт"
            "waiting_input" -> "ждёт ответа"
            "failed" -> "ошибка"
            else -> stage.status
        }
        "${stage.role} $state"
    }
    return listOfNotNull("$done/${run.stages.size}", label).joinToString(" · ")
}
