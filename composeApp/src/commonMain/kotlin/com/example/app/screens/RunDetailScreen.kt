package com.example.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.example.app.components.AppScaffold
import com.example.app.components.Fact
import com.example.app.components.FactGrid
import com.example.app.components.GroupedCard
import com.example.app.components.MenuEntry
import com.example.app.data.AgentizApi
import com.example.app.data.ApiException
import com.example.app.data.InteractionDto
import com.example.app.data.LocalStore
import com.example.app.data.Session
import com.example.app.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 * One run, in full: every stage, the complete log trace and the worker's raw result — the detail a
 * run's card in the task's history only summarises.
 *
 * [runNumber] is passed in from the list the run was opened from purely for the title ("Запуск
 * #3"); the page itself only needs the run's id to load it, so it works just as well reached any
 * other way.
 *
 * [onOpenTask] is handed the project the *loaded run* reports, not the one the destination was
 * built with: a run opened from a notification carries whatever the payload happened to name, and
 * the task screen it leads to has to be reachable even when that was nothing.
 */
@Composable
fun RunDetailScreen(
    session: Session,
    taskId: String,
    runId: String,
    runNumber: Int?,
    menu: List<MenuEntry>,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenTask: (projectId: String?, projectName: String?) -> Unit,
) {
    val api = remember(session.serverUrl) { AgentizApi(session.serverUrl) }
    DisposableEffect(api) { onDispose { api.close() } }

    // A run in a terminal state never changes again on the server, so a cached copy of one is
    // shown as-is with no network call at all — reopening a finished run costs nothing.
    val cached = remember(runId) { LocalStore.loadRun(runId) }
    var run by remember { mutableStateOf(cached) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    var answeringId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            val loaded = api.run(session.token, taskId, runId)
            run = loaded
            LocalStore.saveRun(loaded)
            error = null
        } catch (e: ApiException) {
            error = e.message
        } catch (e: Throwable) {
            error = "Ошибка сети: ${e.message ?: "неизвестная ошибка"}"
        }
    }

    /**
     * Answers a question this run is paused on. The reload only records that it was answered — the
     * run leaves `waiting_input` once the worker collects the answer, which the poll below catches.
     */
    fun answer(interaction: InteractionDto, action: String, content: JsonObject?) {
        if (answeringId != null) return
        answeringId = interaction.id
        scope.launch {
            try {
                api.answerInteraction(session.token, interaction.id, action, content)
                load()
            } catch (e: ApiException) {
                error = e.message
            } catch (e: Throwable) {
                error = "Ошибка сети: ${e.message ?: "неизвестная ошибка"}"
            } finally {
                answeringId = null
            }
        }
    }

    LaunchedEffect(taskId, runId, reloadKey) {
        // A cached run is only trustworthy without a refetch once it is terminal: a run cached
        // while still pending/running would otherwise freeze on the state it happened to be
        // scraped in.
        if (reloadKey == 0 && cached != null && cached.status !in ACTIVE_RUN_STATES) return@LaunchedEffect
        load()
    }

    // A run opened while it is still pending or running keeps updating on its own page exactly as
    // it did inline before — the reader should not have to bounce back to the task and re-open it
    // to see the log grow.
    val active = run?.status in ACTIVE_RUN_STATES
    LaunchedEffect(taskId, runId, active) {
        while (active) {
            delay(2000)
            load()
        }
    }

    val current = run
    AppScaffold(
        title = if (runNumber != null) "Запуск #$runNumber" else "Запуск",
        menu = menu,
        onOpenSettings = onOpenSettings,
        onOpenProfile = onOpenProfile,
        onBack = onBack,
    ) {
        when {
            current == null && error != null -> RetryState(message = error!!, onRetry = { reloadKey++ })
            current == null -> CenterMessage("Загрузка запуска…")
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppTheme.PageBackground)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                if (error != null) {
                    Text(text = error!!, style = AppTheme.Label, color = AppTheme.Danger)
                    Spacer(Modifier.height(12.dp))
                }
                // First on the page, above even a proposal waiting for a decision: reading a run
                // starts with knowing what it was asked to do, and this is the only line that says.
                TaskLinkRow(
                    taskTitle = current.taskTitle,
                    projectName = current.projectName,
                    onClick = { onOpenTask(current.projectId, current.projectName) },
                )
                Spacer(Modifier.height(12.dp))
                // Above the status strip, the instruction and the result: if this run is stuck on
                // a person, that is the only thing on the page worth doing, and the decision is
                // made right here rather than in a block further down that has to be recognised.
                if (current.actionRequired.isNotEmpty()) {
                    ActionRequiredSection(
                        session = session,
                        items = current.actionRequired,
                        onChanged = { reloadKey++ },
                    )
                    Spacer(Modifier.height(16.dp))
                }
                // The run's headline numbers as one fact strip, the way GitHub heads a workflow
                // run with Status / Duration — the prose below is for whoever reads past them.
                GroupedCard {
                    FactGrid(
                        facts = buildList {
                            val (label, color) = runStatusPresentation(current.status)
                            add(Fact("Статус", label, color))
                            formatDuration(current.startedAt, current.finishedAt)?.let {
                                add(Fact("Длительность", it))
                            }
                            current.usage?.let(::totalTokens)?.takeIf { it > 0 }?.let {
                                add(Fact("Токены", formatTokens(it)))
                            }
                        },
                    )
                }
                Spacer(Modifier.height(16.dp))
                // What the agent was actually asked. The task's *name* is above, and for a task
                // started from a one-word comment ("выполни") it says nothing at all — the answer
                // below is unreadable without the question it answers.
                current.instruction?.let { instruction ->
                    InstructionCard(instruction)
                    Spacer(Modifier.height(16.dp))
                }
                RunResult(
                    run = current,
                    interactionBusyId = answeringId,
                    onAnswerInteraction = ::answer,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
