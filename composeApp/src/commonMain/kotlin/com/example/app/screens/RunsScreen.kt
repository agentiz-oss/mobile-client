package com.example.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.example.app.components.AppScaffold
import com.example.app.components.CalendarIcon
import com.example.app.components.ForwardIcon
import com.example.app.components.GroupedCard
import com.example.app.components.InsetDivider
import com.example.app.components.MenuEntry
import com.example.app.components.MetaChip
import com.example.app.components.PullToRefresh
import com.example.app.components.SectionHeader
import com.example.app.components.StatusIcon
import com.example.app.components.StopwatchIcon
import com.example.app.data.AgentizApi
import com.example.app.data.ApiException
import com.example.app.data.RunBoardDto
import com.example.app.data.RunDto
import com.example.app.data.Session
import com.example.app.markdown.markdownToPlainText
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
                // The GitHub-mobile block layout: a grey page carrying one white card per section,
                // the rows inside split by inset hairlines. A section is one item — the board is
                // capped on the server, so the card never grows past what a screen of rows costs.
                LazyColumn(
                    modifier = Modifier.fillMaxSize().background(AppTheme.PageBackground),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    error?.let { message ->
                        item(key = "error") {
                            Text(text = message, style = AppTheme.Label, color = AppTheme.Danger)
                        }
                    }

                    item(key = "active-title") { SectionHeader("Идут сейчас (${current.active.size})") }

                    item(key = "active-card") {
                        GroupedCard {
                            if (current.active.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "Сейчас ничего не выполняется.",
                                        style = AppTheme.Body,
                                        color = AppTheme.Muted,
                                    )
                                }
                            }
                            current.active.forEachIndexed { index, run ->
                                if (index > 0) InsetDivider(start = RowDividerInset)
                                RunBoardRow(run = run, live = true, onOpenRun = onOpenRun)
                            }
                        }
                    }

                    if (current.recent.isNotEmpty()) {
                        item(key = "recent-title") { SectionHeader("Завершились недавно") }
                        item(key = "recent-card") {
                            GroupedCard {
                                current.recent.forEachIndexed { index, run ->
                                    if (index > 0) InsetDivider(start = RowDividerInset)
                                    RunBoardRow(run = run, live = false, onOpenRun = onOpenRun)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dividers align with the text of a row, past the 20.dp status disc and the 12.dp gap after it —
 * the GitHub convention that makes the hairline read as a seam between rows, not a strike through
 * the discs.
 */
private val RowDividerInset = 48.dp

/**
 * One run as a row of the board's card, laid out the way GitHub's app lists workflow runs: the
 * state disc leading, the title over its context line, the measurables worn as chips under them,
 * and a chevron only when the row goes somewhere. A live run is summarised by where it got to and
 * its newest log line; a finished one by what it concluded — the two are what the reader is asking
 * in each case.
 */
@Composable
private fun RunBoardRow(
    run: RunDto,
    live: Boolean,
    onOpenRun: (projectId: String, projectName: String?, taskId: String, runId: String) -> Unit,
) {
    // A run with no task id cannot be opened — the app loads a run through its task — so such a row
    // stays a readable card rather than a button that would go nowhere.
    val open = run.taskId?.let { taskId ->
        { onOpenRun(run.projectId ?: "", run.projectName, taskId, run.id) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { base -> if (open != null) base.clickable(role = Role.Button, onClick = open) else base }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Top-aligned beside the title rather than centred in the row: a row grows with its
        // preview text, and the disc belongs to the run's name, not to the middle of the prose.
        Box(modifier = Modifier.padding(top = 2.dp)) { StatusIcon(run.status, size = 20.dp) }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Column {
                Text(
                    text = run.taskTitle?.takeIf { it.isNotBlank() } ?: "Запуск",
                    style = AppTheme.Body.copy(fontWeight = FontWeight.SemiBold),
                    color = AppTheme.Foreground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val context = listOfNotNull(
                    run.projectName?.takeIf { it.isNotBlank() },
                    run.trigger.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (context.isNotBlank()) {
                    Text(
                        text = context,
                        style = AppTheme.Footnote,
                        color = AppTheme.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val duration = formatDuration(run.startedAt, run.finishedAt)
                if (!live && duration != null) {
                    MetaChip(duration) { tint -> StopwatchIcon(tint, size = 13.dp) }
                }
                formatTimestamp(
                    if (live) run.startedAt ?: run.createdAt else run.finishedAt ?: run.createdAt,
                )?.let { stamp ->
                    MetaChip(stamp) { tint -> CalendarIcon(tint, size = 13.dp) }
                }
                tokensBadge(run.usage)?.let { MetaChip(it) }
            }

            val progress = stageProgress(run)
            if (progress != null) {
                Text(text = progress, style = AppTheme.Footnote, color = AppTheme.Muted)
            }

            if (run.pendingInteractions > 0) {
                Text(
                    text = if (run.pendingInteractions == 1) "ждёт ответа" else "ждёт ответа (${run.pendingInteractions})",
                    style = AppTheme.Label,
                    color = AppTheme.PrimaryForeground,
                    modifier = Modifier
                        .background(AppTheme.Accent, RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }

            val tail = if (live) {
                run.lastLog?.message?.takeIf { it.isNotBlank() }
            } else {
                run.errorMessage?.takeIf { it.isNotBlank() } ?: run.resultSummary?.takeIf { it.isNotBlank() }
            }
            if (tail != null) {
                Text(
                    // A capped preview of agent text: the markup comes off instead of being rendered.
                    text = markdownToPlainText(tail),
                    style = AppTheme.Footnote,
                    color = if (!live && run.errorMessage?.isNotBlank() == true) AppTheme.Danger else AppTheme.Muted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (open != null) {
            Box(modifier = Modifier.align(Alignment.CenterVertically)) {
                ForwardIcon(AppTheme.Muted, size = 14.dp)
            }
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
