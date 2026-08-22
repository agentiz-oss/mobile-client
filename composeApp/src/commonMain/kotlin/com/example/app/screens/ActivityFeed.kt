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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.example.app.components.AppButton
import com.example.app.components.PullToRefresh
import com.example.app.data.ActivityDto
import com.example.app.data.AgentizApi
import com.example.app.data.ApiException
import com.example.app.data.Session
import com.example.app.markdown.markdownToPlainText
import com.example.app.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * The immutable journal: everything that happened across the user's projects, newest first, paged
 * by the server's cursor.
 *
 * A list, not a screen — it is the second tab of [InboxScreen], next to the things that still need
 * a person. The split is the point: what is *waiting* is computed from live entities and shrinks as
 * it is dealt with, while this only ever grows, and mixing the two is what made "требуют действия"
 * unreadable on the screen this came from.
 *
 * Being on screen marks the feed seen: the drawer badge counts rows newer than that mark, and
 * looking at the list is precisely what "seen" means here.
 */
@Composable
fun ActivityFeed(
    session: Session,
    /** A question in focus — the inbox opens its card rather than pushing another screen. */
    onOpenInteraction: (interactionId: String) -> Unit,
    /** Anything that lives on a run's page: proposals, held diffs, finished runs. */
    onOpenRun: (projectId: String, projectName: String?, taskId: String, runId: String) -> Unit,
) {
    val api = remember(session.serverUrl) { AgentizApi(session.serverUrl) }
    DisposableEffect(api) { onDispose { api.close() } }

    var items by remember { mutableStateOf<List<ActivityDto>>(emptyList()) }
    var nextBefore by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            val page = api.activities(session.token)
            items = page.items
            nextBefore = page.nextBefore
            loaded = true
            error = null
            // Seen means "the list was on screen with fresh rows in it" — after the fetch, so a
            // failed load never silently clears the badge.
            runCatching { api.markActivitiesSeen(session.token) }
        } catch (e: ApiException) {
            error = e.message
        } catch (e: Throwable) {
            error = "Ошибка сети: ${e.message ?: "неизвестная ошибка"}"
        } finally {
            refreshing = false
        }
    }

    fun loadMore() {
        val cursor = nextBefore ?: return
        if (loadingMore) return
        loadingMore = true
        scope.launch {
            try {
                val page = api.activities(session.token, before = cursor)
                // Dedupe on id: a row created between the two requests shifts the pages, and a
                // duplicate key would crash the LazyColumn.
                val known = items.mapTo(mutableSetOf()) { it.id }
                items = items + page.items.filter { it.id !in known }
                nextBefore = page.nextBefore
            } catch (e: Throwable) {
                error = e.message ?: "Ошибка сети"
            } finally {
                loadingMore = false
            }
        }
    }

    LaunchedEffect(reloadKey) { load() }

        when {
            !loaded && error != null -> RetryState(message = error!!, onRetry = { reloadKey++ })
            !loaded -> CenterMessage("Загрузка активностей…")
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
                        item(key = "error") { Text(text = message, style = AppTheme.Label, color = AppTheme.Danger) }
                    }

                    if (items.isEmpty()) {
                        item(key = "feed-empty") {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(text = "Пока ничего не происходило.", style = AppTheme.Body, color = AppTheme.Muted)
                            }
                        }
                    }

                    items(items, key = { it.id }) { row ->
                        ActivityCard(
                            activity = row,
                            onClick = when {
                                row.interactionId != null -> ({ onOpenInteraction(row.interactionId) })
                                row.taskId != null && row.runId != null ->
                                    ({ onOpenRun(row.projectId, row.projectName, row.taskId, row.runId) })
                                else -> null
                            },
                        )
                    }

                    if (nextBefore != null) {
                        item(key = "load-more") {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                AppButton(
                                    text = if (loadingMore) "Загружается…" else "Показать ещё",
                                    onClick = ::loadMore,
                                    enabled = !loadingMore,
                                )
                            }
                        }
                    }
                }
            }
    }
}

/** One journal row. Actionable kinds are only *coloured* — whether they still need anything is the summary's business. */
@Composable
private fun ActivityCard(activity: ActivityDto, onClick: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.Radius))
            .let { base -> if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base }
            .border(1.dp, AppTheme.Border, RoundedCornerShape(AppTheme.Radius))
            .background(AppTheme.Surface, RoundedCornerShape(AppTheme.Radius))
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = activity.title,
                style = AppTheme.Body,
                color = if (activity.kind == "action_required") AppTheme.Foreground else AppTheme.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            activity.createdAt?.let {
                Text(text = formatTimestamp(it) ?: "", style = AppTheme.Label, color = AppTheme.Muted)
            }
        }
        val context = listOfNotNull(
            activity.taskTitle?.takeIf { it.isNotBlank() },
            activity.projectName?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        if (context.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(text = context, style = AppTheme.Label, color = AppTheme.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (activity.body.isNotBlank() && activity.body != activity.title) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = markdownToPlainText(activity.body),
                style = AppTheme.Label,
                color = AppTheme.Muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
