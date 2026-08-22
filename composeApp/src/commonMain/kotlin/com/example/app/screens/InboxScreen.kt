package com.example.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.example.app.components.AlertIcon
import com.example.app.components.AppButton
import com.example.app.components.AppScaffold
import com.example.app.components.Badge
import com.example.app.components.BadgeSize
import com.example.app.components.BadgeVariant
import com.example.app.components.BellIcon
import com.example.app.components.ButtonSize
import com.example.app.components.ButtonVariant
import com.example.app.components.DotFillIcon
import com.example.app.components.GitBranchIcon
import com.example.app.components.GitPullRequestIcon
import com.example.app.components.IssueOpenedIcon
import com.example.app.components.MenuEntry
import com.example.app.components.PullToRefresh
import com.example.app.data.AgentizApi
import com.example.app.data.ApiException
import com.example.app.data.InboxActionDto
import com.example.app.data.InboxItemDto
import com.example.app.data.InteractionDto
import com.example.app.data.ProposalDto
import com.example.app.data.Session
import com.example.app.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/** How often the list re-checks. A question answered in the panel has to leave the screen on its own. */
private const val INBOX_POLL_MS = 5000L

/** The two halves of the screen: what is waiting, and everything that happened. */
enum class InboxTab { Actionable, Feed }

/**
 * Everything that needs a person, in one place.
 *
 * The app used to have two doors onto overlapping content — «Вопросы», which could answer a
 * question but knew nothing about reviews, and «Активности», whose "требуют действия" block could
 * only navigate away to a run's page. A reader had to know in advance which door their particular
 * kind of waiting was behind. Here every kind is one card, and the card is where it gets decided:
 * the row states what is being asked and the facts to decide on ([InboxItemDto.headline] /
 * `facts`, both spelled by the server), and its buttons open the answer form or the review block
 * in place — the list keeps its position, which is what makes a queue of five things dischargeable
 * in one sitting.
 *
 * Only the second tab is the immutable journal, and it is the same feed as before ([ActivityFeed]).
 * The first tab is computed from live entities on every poll, so an item disappears the moment
 * somebody else deals with it, wherever they did it from.
 */
@Composable
fun InboxScreen(
    session: Session,
    menu: List<MenuEntry>,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenTask: (projectId: String, projectName: String?, taskId: String) -> Unit,
    onOpenRun: (projectId: String, projectName: String?, taskId: String, runId: String) -> Unit,
    /**
     * The question a tapped notification asked for: its card is opened, and if it is not waiting
     * any more it is fetched by id and shown as closed — a push tapped ten minutes late must end
     * in an explanation, not in a list that silently does not contain it.
     */
    focusInteractionId: String? = null,
    initialTab: InboxTab = InboxTab.Actionable,
) {
    val api = remember(session.serverUrl) { AgentizApi(session.serverUrl) }
    DisposableEffect(api) { onDispose { api.close() } }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    var tab by remember { mutableStateOf(initialTab) }
    var items by remember { mutableStateOf<List<InboxItemDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }

    // One card at a time is open, and one action at a time is in flight: a slow answer must not be
    // sendable twice while the neighbouring cards stay usable.
    var expandedId by remember { mutableStateOf<String?>(null) }
    var expandedInteraction by remember { mutableStateOf<InteractionDto?>(null) }
    var expandedProposal by remember { mutableStateOf<ProposalDto?>(null) }
    var expandedMode by remember { mutableStateOf<String?>(null) }
    var expandLoading by remember { mutableStateOf(false) }
    var busyId by remember { mutableStateOf<String?>(null) }

    // Set only when the notification's question is no longer pending.
    var closedFocus by remember { mutableStateOf<InteractionDto?>(null) }

    suspend fun load() {
        try {
            val summary = api.activitySummary(session.token)
            items = summary.items
            if (focusInteractionId != null && summary.items.none { it.interactionId == focusInteractionId }) {
                // Best effort: a question the server will not hand over is simply not shown, never
                // an error on the list.
                closedFocus = runCatching { api.interaction(session.token, focusInteractionId) }.getOrNull()
            } else {
                closedFocus = null
            }
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

    LaunchedEffect(tab) {
        if (tab != InboxTab.Actionable) return@LaunchedEffect
        while (true) {
            delay(INBOX_POLL_MS)
            load()
        }
    }

    /**
     * Opening a card fetches the entity behind it — the question with its schema, the proposal with
     * its diff and target branch. Deliberately lazy: the list itself renders from the summary
     * alone, and a screen polling every five seconds must not also poll two payloads nobody has
     * opened.
     */
    fun expand(item: InboxItemDto, mode: String?) {
        if (expandedId == item.id && expandedMode == mode) {
            expandedId = null
            return
        }
        expandedId = item.id
        expandedMode = mode
        expandedInteraction = null
        expandedProposal = null
        expandLoading = true
        scope.launch {
            try {
                when {
                    item.interactionId != null ->
                        expandedInteraction = api.interaction(session.token, item.interactionId)
                    item.proposalId != null ->
                        expandedProposal = api.proposals(session.token).firstOrNull { it.id == item.proposalId }
                }
                error = null
            } catch (e: ApiException) {
                error = e.message
            } catch (e: Throwable) {
                error = "Ошибка сети: ${e.message ?: "неизвестная ошибка"}"
            } finally {
                expandLoading = false
            }
        }
    }

    /** Every decision ends the same way: close the card, reload, let the list re-decide what is left. */
    fun submit(item: InboxItemDto, call: suspend () -> Unit) {
        if (busyId != null) return
        busyId = item.id
        scope.launch {
            try {
                call()
                // Drop it locally at once. The next poll confirms it, but the card must not sit
                // there decided-and-still-asking in the meantime.
                items = items?.filterNot { it.id == item.id }
                expandedId = null
                expandedMode = null
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

    fun act(item: InboxItemDto, action: InboxActionDto) {
        val taskId = item.taskId
        val runId = item.runId
        when (action.key) {
            "answer", "approve", "reject" -> expand(item, action.key)
            // The three that need no form: another attempt, the held diff, and closing a task
            // whose remaining resolution happens outside Agentiz. The caption and, for
            // `close_task`, the status itself are the server's — see InboxAction.value.
            "rerun" -> if (taskId != null) submit(item) { api.runTask(session.token, taskId) }
            "apply_diff" -> if (taskId != null && runId != null) {
                submit(item) { api.applyRunDiff(session.token, taskId, runId) }
            }
            "close_task" -> if (taskId != null) {
                submit(item) { api.setTaskStatus(session.token, taskId, action.value ?: "done") }
            }
            "open_run" -> {
                val taskId = item.taskId
                val runId = item.runId
                if (taskId != null && runId != null) onOpenRun(item.projectId, item.projectName, taskId, runId)
            }
            // No in-app browser: a pull request lives in the provider's UI, which is where the
            // decision about it is actually made.
            "open_url" -> item.url?.let { runCatching { uriHandler.openUri(it) } }
            else -> Unit
        }
    }

    val current = items
    AppScaffold(
        title = "Входящие",
        subtitle = current?.size?.takeIf { it > 0 }?.let { "$it требуют действия" },
        menu = menu,
        onOpenSettings = onOpenSettings,
        onOpenProfile = onOpenProfile,
        onBack = onBack,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            InboxTabs(tab = tab, onSelect = { tab = it })
            when (tab) {
                InboxTab.Feed -> ActivityFeed(
                    session = session,
                    onOpenInteraction = { interactionId ->
                        // Staying on this screen: the question's own card is one tab away, and
                        // pushing a second screen for it would rebuild the same list.
                        tab = InboxTab.Actionable
                        items?.firstOrNull { it.interactionId == interactionId }?.let { expand(it, "answer") }
                    },
                    onOpenRun = onOpenRun,
                )

                InboxTab.Actionable -> when {
                    current == null && error != null -> RetryState(message = error!!, onRetry = { reloadKey++ })
                    current == null -> CenterMessage("Загрузка…")
                    else -> PullToRefresh(
                        refreshing = refreshing,
                        onRefresh = {
                            refreshing = true
                            reloadKey++
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxSize().background(AppTheme.Background)) {
                            error?.let { message ->
                                item(key = "error") {
                                    Text(
                                        text = message,
                                        style = AppTheme.Label,
                                        color = AppTheme.Danger,
                                        modifier = Modifier.padding(16.dp),
                                    )
                                }
                            }

                            closedFocus?.let { closed ->
                                item(key = "closed-${closed.id}") {
                                    Column(modifier = Modifier.padding(16.dp)) { ClosedInteractionNote(closed) }
                                }
                            }

                            if (current.isEmpty() && closedFocus == null) {
                                item(key = "empty") {
                                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "Ничего не ждёт вашего участия.",
                                            style = AppTheme.Body,
                                            color = AppTheme.Muted,
                                        )
                                    }
                                }
                            }

                            items(current, key = { it.id }) { row ->
                                InboxRow(
                                    item = row,
                                    busy = busyId == row.id,
                                    expanded = expandedId == row.id,
                                    loadingDetail = expandedId == row.id && expandLoading,
                                    interaction = if (expandedId == row.id) expandedInteraction else null,
                                    proposal = if (expandedId == row.id) expandedProposal else null,
                                    initialMode = if (expandedId == row.id) expandedMode else null,
                                    onAction = { action -> act(row, action) },
                                    // The row itself goes where the thing lives — its run, or its
                                    // task when there is no run. GitHub's inbox rows navigate on
                                    // tap and keep their buttons for the decision itself.
                                    onOpen = {
                                        val taskId = row.taskId
                                        val runId = row.runId
                                        when {
                                            taskId != null && runId != null ->
                                                onOpenRun(row.projectId, row.projectName, taskId, runId)
                                            taskId != null -> onOpenTask(row.projectId, row.projectName, taskId)
                                        }
                                    },
                                    onAnswer = { answerAction, content ->
                                        val interactionId = row.interactionId
                                        if (interactionId != null) {
                                            submit(row) { api.answerInteraction(session.token, interactionId, answerAction, content) }
                                        }
                                    },
                                    onApprove = { revision, branch, message ->
                                        val proposalId = row.proposalId
                                        if (proposalId != null) {
                                            submit(row) { api.approveProposal(session.token, proposalId, revision, branch, message) }
                                        }
                                    },
                                    onReject = { revision ->
                                        val proposalId = row.proposalId
                                        if (proposalId != null) {
                                            submit(row) { api.rejectProposal(session.token, proposalId, revision) }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The two-pill switch over the list. A tab bar, not a second screen: both halves are one inbox. */
@Composable
private fun InboxTabs(tab: InboxTab, onSelect: (InboxTab) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(AppTheme.Background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InboxTabPill("Требуют действия", tab == InboxTab.Actionable) { onSelect(InboxTab.Actionable) }
            InboxTabPill("Лента", tab == InboxTab.Feed) { onSelect(InboxTab.Feed) }
        }
        RowDivider()
    }
}

/** The hairline GitHub draws between two inbox rows: full width, no inset. */
@Composable
private fun RowDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(AppTheme.Border))
}

@Composable
private fun InboxTabPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = AppTheme.Label,
        color = if (selected) AppTheme.PrimaryForeground else AppTheme.Foreground,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) AppTheme.Primary else AppTheme.Surface, RoundedCornerShape(999.dp))
            .border(1.dp, AppTheme.Border, RoundedCornerShape(999.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/**
 * One thing to decide, laid out the way GitHub's mobile inbox lays out a notification.
 *
 * Not a card: a full-bleed row on white, separated from its neighbours by a hairline, with a
 * leading gutter carrying the type glyph and, under it, the dot that says this one is still open.
 * The text column reads top to bottom in the order a person needs it — whose task this is, then
 * what is being asked, then the kind of attention and the facts to decide on — and the row's own
 * tap goes to the run behind it while the buttons decide it in place.
 *
 * The previous version was a bordered card whose body was the first sentence of the agent's
 * output, which reads like an explanation and is not one. Nothing here is the agent's prose.
 */
@Composable
internal fun InboxRow(
    item: InboxItemDto,
    busy: Boolean = false,
    expanded: Boolean = false,
    loadingDetail: Boolean = false,
    interaction: InteractionDto? = null,
    proposal: ProposalDto? = null,
    initialMode: String? = null,
    onAction: (InboxActionDto) -> Unit,
    onOpen: () -> Unit,
    onAnswer: (action: String, content: JsonObject?) -> Unit = { _, _ -> },
    onApprove: (revision: Int, targetBranch: String?, commitMessage: String?) -> Unit = { _, _, _ -> },
    onReject: (revision: Int) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.Background)
            .clickable(role = Role.Button, onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The gutter: what kind of attention this is, and the dot that marks it still open.
            Column(
                modifier = Modifier.width(20.dp).padding(top = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                KindIcon(item.kind)
                DotFillIcon(dotColor(item.kind), size = 10.dp)
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val context = listOfNotNull(
                        item.projectName?.takeIf { it.isNotBlank() },
                        item.taskTitle?.takeIf { it.isNotBlank() },
                    ).joinToString(" · ")
                    Text(
                        text = context.ifBlank { item.badge },
                        style = AppTheme.Footnote,
                        color = AppTheme.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    formatWaiting(item.waitingSince)?.let { age ->
                        Text(text = age, style = AppTheme.Footnote, color = AppTheme.Muted)
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.headline,
                    style = AppTheme.Body,
                    color = AppTheme.Foreground,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Badge(text = item.badge, variant = badgeVariant(item.kind), size = BadgeSize.Sm)
                    item.facts?.takeIf { it.isNotBlank() }?.let { facts ->
                        Text(
                            text = facts,
                            style = AppTheme.Footnote,
                            color = AppTheme.Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Two lines of "что это значит и что сделают кнопки". The list is scannable
                // without it only for a question; «ревью, 0 файлов, [Отклонить]» is not a choice
                // anybody can make from three words. The full text is on the run's own screen.
                item.explain?.takeIf { it.isNotBlank() }?.let { explain ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = explain,
                        style = AppTheme.Footnote,
                        color = AppTheme.Muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                formatRemaining(item.expiresAt)?.let { left ->
                    Spacer(Modifier.height(4.dp))
                    // The run is cancelled when this passes, so it is a consequence, not a footnote.
                    Text(text = "ответ ждут ещё $left", style = AppTheme.Footnote, color = AppTheme.Warning)
                }

                if (item.actions.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
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
                                    "primary" -> ButtonVariant.Accent
                                    "danger" -> ButtonVariant.Destructive
                                    else -> ButtonVariant.Outline
                                },
                            )
                        }
                    }
                }
            }
        }

        if (expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                when {
                    loadingDetail -> Text(text = "Загрузка…", style = AppTheme.Label, color = AppTheme.Muted)
                    interaction != null -> InteractionCard(
                        interaction = interaction,
                        busy = busy,
                        onAnswer = onAnswer,
                    )
                    proposal != null -> ProposalReviewSection(
                        proposal = proposal,
                        busy = busy,
                        initialMode = initialMode?.takeIf { it == "approve" || it == "reject" },
                        onApprove = onApprove,
                        onReject = onReject,
                    )
                    // The entity is gone: somebody dealt with it between the list and the tap.
                    else -> Text(
                        text = "Это уже решено — список сейчас обновится.",
                        style = AppTheme.Label,
                        color = AppTheme.Muted,
                    )
                }
            }
        }

        RowDivider()
    }
}

/** The glyph in the row's gutter: what kind of attention this is, before any word is read. */
@Composable
private fun KindIcon(kind: String) {
    when (kind) {
        "question" -> BellIcon(AppTheme.Muted, size = 18.dp)
        "push_failed", "reset_failed" -> AlertIcon(AppTheme.Danger, size = 18.dp)
        "review" -> GitPullRequestIcon(AppTheme.Muted, size = 18.dp)
        "run_failed" -> AlertIcon(AppTheme.Danger, size = 18.dp)
        "no_changes" -> IssueOpenedIcon(AppTheme.Muted, size = 18.dp)
        "held_diff" -> GitBranchIcon(AppTheme.Muted, size = 18.dp)
        "pr" -> GitPullRequestIcon(AppTheme.Accent, size = 18.dp)
        else -> IssueOpenedIcon(AppTheme.Muted, size = 18.dp)
    }
}

/** Blue for "somebody has to look", red for "something is broken" — the same split as the glyph. */
private fun dotColor(kind: String) = when (kind) {
    "push_failed", "reset_failed", "run_failed" -> AppTheme.Danger
    else -> AppTheme.Accent
}

private fun badgeVariant(kind: String) = when (kind) {
    "push_failed", "reset_failed", "run_failed" -> BadgeVariant.Destructive
    "question", "pr" -> BadgeVariant.Accent
    else -> BadgeVariant.Secondary
}
