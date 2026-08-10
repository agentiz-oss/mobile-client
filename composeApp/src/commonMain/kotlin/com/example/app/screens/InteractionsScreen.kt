package com.example.app.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.example.app.components.AppScaffold
import com.example.app.components.MenuEntry
import com.example.app.components.PullToRefresh
import com.example.app.data.AgentizApi
import com.example.app.data.ApiException
import com.example.app.data.InteractionDto
import com.example.app.data.Session
import com.example.app.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How often the list re-checks. Slower than a task's own poll: this is a background watch. */
private const val INTERACTIONS_POLL_MS = 5000L

/**
 * Every question an agent is waiting on, across all of the user's projects — the app's counterpart
 * to the dashboard's "Вопросы" page.
 *
 * Each of these has a pipeline run parked behind it, so the screen keeps polling for as long as it
 * is open: a question that arrives while the user is looking at the list has to appear on its own,
 * and one somebody else answered in the dashboard has to disappear the same way.
 */
@Composable
fun InteractionsScreen(
    session: Session,
    menu: List<MenuEntry>,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenTask: (projectId: String, projectName: String?, taskId: String) -> Unit,
) {
    val api = remember(session.serverUrl) { AgentizApi(session.serverUrl) }
    DisposableEffect(api) { onDispose { api.close() } }
    val scope = rememberCoroutineScope()

    var interactions by remember { mutableStateOf<List<InteractionDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    // Which question is mid-submit — one at a time, so a slow answer cannot be double-sent while
    // the neighbouring cards stay usable.
    var busyId by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        try {
            interactions = api.pendingInteractions(session.token)
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
            delay(INTERACTIONS_POLL_MS)
            load()
        }
    }

    fun answer(interaction: InteractionDto, action: String, content: kotlinx.serialization.json.JsonObject?) {
        if (busyId != null) return
        busyId = interaction.id
        scope.launch {
            try {
                api.answerInteraction(session.token, interaction.id, action, content)
                // Drop it locally at once. The next poll confirms it, but the card must not sit
                // there answered-and-still-asking in the meantime.
                interactions = interactions?.filterNot { it.id == interaction.id }
                error = null
                load()
            } catch (e: ApiException) {
                error = e.message
            } catch (e: Throwable) {
                error = "Ошибка сети: ${e.message ?: "неизвестная ошибка"}"
            } finally {
                busyId = null
            }
        }
    }

    val current = interactions
    AppScaffold(
        title = "Вопросы",
        subtitle = current?.size?.takeIf { it > 0 }?.let { "$it ждут ответа" },
        menu = menu,
        onOpenSettings = onOpenSettings,
        onOpenProfile = onOpenProfile,
        onBack = onBack,
    ) {
        when {
            current == null && error != null -> RetryState(message = error!!, onRetry = { reloadKey++ })
            current == null -> CenterMessage("Загрузка вопросов…")
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

                    if (current.isEmpty()) {
                        item(key = "empty") {
                            Box(
                                modifier = Modifier.fillParentMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Агенты ничего не спрашивают.",
                                    style = AppTheme.Body,
                                    color = AppTheme.Muted,
                                )
                            }
                        }
                    }

                    items(current, key = { it.id }) { interaction ->
                        InteractionCard(
                            interaction = interaction,
                            busy = busyId == interaction.id,
                            onAnswer = { action, content -> answer(interaction, action, content) },
                            showContext = true,
                            onOpenTask = interaction.taskId?.let { taskId ->
                                { onOpenTask(interaction.projectId, interaction.projectName, taskId) }
                            },
                        )
                    }
                }
            }
        }
    }
}
