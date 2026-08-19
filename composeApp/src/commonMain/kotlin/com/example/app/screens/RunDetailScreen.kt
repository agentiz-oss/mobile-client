package com.example.app.screens

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
import com.example.app.components.MenuEntry
import com.example.app.data.AgentizApi
import com.example.app.data.ApiException
import com.example.app.data.InteractionDto
import com.example.app.data.LocalStore
import com.example.app.data.ProposalDto
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
    // The run's workspace proposal, when one still needs a decision. Fetched beside the run rather
    // than embedded in it: the proposal moves on its own (an approve from the dashboard, a worker
    // finishing the push), and the actionable list is the endpoint that already knows the answer.
    var proposal by remember { mutableStateOf<ProposalDto?>(null) }
    var decisionBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            val loaded = api.run(session.token, taskId, runId)
            run = loaded
            LocalStore.saveRun(loaded)
            proposal = runCatching { api.proposals(session.token).firstOrNull { it.runId == runId } }
                .getOrDefault(proposal)
            error = null
        } catch (e: ApiException) {
            error = e.message
        } catch (e: Throwable) {
            error = "Ошибка сети: ${e.message ?: "неизвестная ошибка"}"
        }
    }

    /** Sends one decision; a 409 (stale revision, somebody was faster) surfaces and a reload follows either way. */
    fun decide(call: suspend () -> Unit) {
        if (decisionBusy) return
        decisionBusy = true
        scope.launch {
            try {
                call()
                proposal = null
                error = null
            } catch (e: ApiException) {
                error = e.message
            } catch (e: Throwable) {
                error = "Ошибка сети: ${e.message ?: "неизвестная ошибка"}"
            } finally {
                decisionBusy = false
                load()
            }
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
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                if (error != null) {
                    Text(text = error!!, style = AppTheme.Label, color = AppTheme.Danger)
                    Spacer(Modifier.height(12.dp))
                }
                proposal?.let { pending ->
                    ProposalReviewSection(
                        proposal = pending,
                        busy = decisionBusy,
                        onApprove = { revision, targetBranch, commitMessage ->
                            decide { api.approveProposal(session.token, pending.id, revision, targetBranch, commitMessage) }
                        },
                        onReject = { revision ->
                            decide { api.rejectProposal(session.token, pending.id, revision) }
                        },
                    )
                    Spacer(Modifier.height(24.dp))
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
