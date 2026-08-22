package com.example.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.example.app.components.AppButton
import com.example.app.components.Badge
import com.example.app.components.BadgeSize
import com.example.app.components.BadgeVariant
import com.example.app.components.ButtonSize
import com.example.app.components.ButtonVariant
import com.example.app.data.AgentizApi
import com.example.app.data.ApiException
import com.example.app.data.InboxActionDto
import com.example.app.data.InboxItemDto
import com.example.app.data.InteractionDto
import com.example.app.data.ProposalDto
import com.example.app.data.Session
import com.example.app.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * «Что нужно сделать» — the block a run's and a task's screen lead with.
 *
 * The inbox answers "что вообще ждёт меня"; this answers the same question for the one thing the
 * reader already has open, and it is the only place on those screens where the decision is made.
 * A run used to state its predicament as a status word ("ждёт ревью") with the actual controls
 * further down the page, past the result, in a block a first-time reader does not recognise as a
 * decision — and for the states with no obvious remedy (a review of an empty diff, a run that just
 * failed) there was nothing at all.
 *
 * Everything it renders — the chip, the sentence explaining what each button does, the buttons —
 * comes from the server's projection (`InboxItem`), so the phone never invents its own account of
 * a server-side state machine. What lives here is only the dispatch: which endpoint a key calls,
 * and which of them expand a form in place instead.
 */
@Composable
fun ActionRequiredSection(
    session: Session,
    items: List<InboxItemDto>,
    /** Where «Открыть запуск» goes. Absent on the run screen — the server drops that action there. */
    onOpenRun: (InboxItemDto) -> Unit = {},
    /** Called after anything is decided: the host reloads and the item disappears on its own. */
    onChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val api = remember(session.serverUrl) { AgentizApi(session.serverUrl) }
    DisposableEffect(api) { onDispose { api.close() } }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    var expandedId by remember { mutableStateOf<String?>(null) }
    var expandedMode by remember { mutableStateOf<String?>(null) }
    var interaction by remember { mutableStateOf<InteractionDto?>(null) }
    var proposal by remember { mutableStateOf<ProposalDto?>(null) }
    var loadingDetail by remember { mutableStateOf(false) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    /** Same laziness as the inbox: the entity behind a row is fetched when the row is opened. */
    fun expand(item: InboxItemDto, mode: String) {
        if (expandedId == item.id && expandedMode == mode) {
            expandedId = null
            return
        }
        expandedId = item.id
        expandedMode = mode
        interaction = null
        proposal = null
        loadingDetail = true
        scope.launch {
            try {
                when {
                    item.interactionId != null -> interaction = api.interaction(session.token, item.interactionId)
                    item.proposalId != null -> proposal = api.proposals(session.token).firstOrNull { it.id == item.proposalId }
                }
                error = null
            } catch (e: ApiException) {
                error = e.message
            } catch (e: Throwable) {
                error = "Ошибка сети: ${e.message ?: "неизвестная ошибка"}"
            } finally {
                loadingDetail = false
            }
        }
    }

    fun submit(item: InboxItemDto, call: suspend () -> Unit) {
        if (busyId != null) return
        busyId = item.id
        scope.launch {
            try {
                call()
                expandedId = null
                expandedMode = null
                error = null
                onChanged()
            } catch (e: ApiException) {
                error = e.message
            } catch (e: Throwable) {
                error = "Ошибка сети: ${e.message ?: "неизвестная ошибка"}"
            } finally {
                busyId = null
            }
        }
    }

    fun act(item: InboxItemDto, action: InboxActionDto) {
        val taskId = item.taskId
        val runId = item.runId
        when (action.key) {
            "answer", "approve", "reject" -> expand(item, action.key)
            "rerun" -> if (taskId != null) submit(item) { api.runTask(session.token, taskId) }
            "apply_diff" -> if (taskId != null && runId != null) {
                submit(item) { api.applyRunDiff(session.token, taskId, runId) }
            }
            // The server picks between «сделано» and «не будем делать»; the phone only passes it on.
            "close_task" -> if (taskId != null) {
                submit(item) { api.setTaskStatus(session.token, taskId, action.value ?: "done") }
            }
            "open_run" -> onOpenRun(item)
            "open_url" -> item.url?.let { runCatching { uriHandler.openUri(it) } }
            else -> Unit
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        SectionTitle(if (items.size == 1) "Требуется ваше участие" else "Требуется ваше участие (${items.size})")
        Spacer(Modifier.height(8.dp))
        error?.let { message ->
            Text(text = message, style = AppTheme.Label, color = AppTheme.Danger)
            Spacer(Modifier.height(8.dp))
        }
        items.forEachIndexed { index, item ->
            if (index > 0) Spacer(Modifier.height(12.dp))
            ActionRequiredCard(
                item = item,
                busy = busyId == item.id,
                expanded = expandedId == item.id,
                loadingDetail = expandedId == item.id && loadingDetail,
                interaction = if (expandedId == item.id) interaction else null,
                proposal = if (expandedId == item.id) proposal else null,
                mode = if (expandedId == item.id) expandedMode else null,
                onAction = { action -> act(item, action) },
                onAnswer = { answerAction, content ->
                    val interactionId = item.interactionId
                    if (interactionId != null) {
                        submit(item) { api.answerInteraction(session.token, interactionId, answerAction, content) }
                    }
                },
                onApprove = { revision, branch, message ->
                    val proposalId = item.proposalId
                    if (proposalId != null) {
                        submit(item) { api.approveProposal(session.token, proposalId, revision, branch, message) }
                    }
                },
                onReject = { revision ->
                    val proposalId = item.proposalId
                    if (proposalId != null) {
                        submit(item) { api.rejectProposal(session.token, proposalId, revision) }
                    }
                },
            )
        }
    }
}

/**
 * One thing to settle, as a card rather than as an inbox row: here it is not one of a list of
 * twenty but the reason the reader is on this page, so it gets the tint of its severity, the whole
 * explanation instead of a clamped line, and its buttons at full width of the card.
 */
@Composable
internal fun ActionRequiredCard(
    item: InboxItemDto,
    busy: Boolean = false,
    expanded: Boolean = false,
    loadingDetail: Boolean = false,
    interaction: InteractionDto? = null,
    proposal: ProposalDto? = null,
    mode: String? = null,
    onAction: (InboxActionDto) -> Unit,
    onAnswer: (action: String, content: kotlinx.serialization.json.JsonObject?) -> Unit = { _, _ -> },
    onApprove: (revision: Int, targetBranch: String?, commitMessage: String?) -> Unit = { _, _, _ -> },
    onReject: (revision: Int) -> Unit = {},
) {
    val broken = item.kind == "push_failed" || item.kind == "reset_failed" || item.kind == "run_failed"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.Radius))
            .border(1.dp, if (broken) AppTheme.Danger else AppTheme.Accent, RoundedCornerShape(AppTheme.Radius))
            .background(if (broken) AppTheme.DangerSubtle else AppTheme.AccentSubtle)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Badge(
                text = item.badge,
                variant = if (broken) BadgeVariant.Destructive else BadgeVariant.Accent,
                size = BadgeSize.Sm,
            )
            formatWaiting(item.waitingSince)?.let { age ->
                Text(text = "ждёт $age", style = AppTheme.Footnote, color = AppTheme.Muted)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(text = item.headline, style = AppTheme.Body, color = AppTheme.Foreground)
        item.facts?.takeIf { it.isNotBlank() }?.let { facts ->
            Spacer(Modifier.height(4.dp))
            Text(text = facts, style = AppTheme.Footnote, color = AppTheme.Muted)
        }
        // The sentence that turns a state into a choice. Never clamped here: this is the page the
        // reader opened *because* something is wrong with it.
        item.explain?.takeIf { it.isNotBlank() }?.let { explain ->
            Spacer(Modifier.height(8.dp))
            Text(text = explain, style = AppTheme.Footnote, color = AppTheme.Foreground)
        }
        formatRemaining(item.expiresAt)?.let { left ->
            Spacer(Modifier.height(6.dp))
            Text(text = "ответ ждут ещё $left", style = AppTheme.Footnote, color = AppTheme.Warning)
        }
        if (item.actions.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item.actions.forEach { action ->
                    AppButton(
                        text = action.label,
                        onClick = { onAction(action) },
                        enabled = !busy,
                        size = ButtonSize.Sm,
                        variant = when (action.style) {
                            "primary" -> ButtonVariant.Default
                            "danger" -> ButtonVariant.Destructive
                            else -> ButtonVariant.Secondary
                        },
                    )
                }
            }
        }
        if (expanded) {
            Spacer(Modifier.height(12.dp))
            when {
                loadingDetail -> Text(text = "Загрузка…", style = AppTheme.Label, color = AppTheme.Muted)
                interaction != null -> InteractionCard(interaction = interaction, busy = busy, onAnswer = onAnswer)
                proposal != null -> ProposalReviewSection(
                    proposal = proposal,
                    busy = busy,
                    initialMode = mode?.takeIf { it == "approve" || it == "reject" },
                    onApprove = onApprove,
                    onReject = onReject,
                )
                else -> Text(
                    text = "Это уже решено — экран сейчас обновится.",
                    style = AppTheme.Label,
                    color = AppTheme.Muted,
                )
            }
        }
    }
}
