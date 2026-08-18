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
import com.example.app.data.HarnessSubscriptionDto
import com.example.app.data.HarnessWindowDto
import com.example.app.data.Session
import com.example.app.data.SubscriptionWorkerDto
import com.example.app.data.WorkerDto
import com.example.app.data.WorkerHarnessDto
import com.example.app.theme.AppTheme
import kotlinx.coroutines.delay

/**
 * How often the page re-reads. Usage is reported by each worker every 120 s, so anything faster
 * only re-renders the same numbers — this is slow enough to be cheap and fast enough that a limit
 * that just closed shows up while the user is still looking at the screen.
 */
private const val CAPACITY_POLL_MS = 30_000L

/** The two halves of the page. Kept as a type so the tab strip and the body cannot disagree. */
private enum class CapacityTab(val label: String) {
    Workers("Воркеры"),
    Subscriptions("Подписки"),
}

/**
 * Where the work can actually run: every worker with the harness limits it runs under, and — in the
 * second tab — the same limits seen from the subscription they belong to.
 *
 * Both tabs exist because a limit is not a property of a machine. It belongs to an *account*: two
 * workers signed into one Claude subscription run out together, and one worker running Claude and
 * Codex runs out of each independently. The worker tab answers "может ли эта машина взять задачу
 * сейчас", the subscription tab answers "что именно кончилось и когда обновится" — neither is
 * derivable from the other by looking.
 *
 * Everything shown is read-only. Clearing a limit by hand is an operator action that lives in the
 * admin panel and in MCP (`agentiz.manageWorker`), not on a phone.
 */
@Composable
fun WorkersScreen(
    session: Session,
    menu: List<MenuEntry>,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val api = remember(session.serverUrl) { AgentizApi(session.serverUrl) }
    DisposableEffect(api) { onDispose { api.close() } }

    var tab by remember { mutableStateOf(CapacityTab.Workers) }
    var workers by remember { mutableStateOf<List<WorkerDto>?>(null) }
    var subscriptions by remember { mutableStateOf<List<HarnessSubscriptionDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }

    // Both lists are loaded together, whichever tab is open: switching tabs is then instant, and the
    // counts in the tab strip are true for the tab the user is *not* on — which is the whole point
    // of showing them there.
    suspend fun load() {
        try {
            workers = api.workers(session.token)
            subscriptions = api.harnessSubscriptions(session.token)
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
            delay(CAPACITY_POLL_MS)
            load()
        }
    }

    val currentWorkers = workers
    val currentSubscriptions = subscriptions
    val loaded = currentWorkers != null && currentSubscriptions != null

    AppScaffold(
        title = "Воркеры",
        subtitle = currentSubscriptions
            ?.count { it.exhausted }
            ?.takeIf { it > 0 }
            ?.let { "$it подписка исчерпана" },
        menu = menu,
        onOpenSettings = onOpenSettings,
        onOpenProfile = onOpenProfile,
        onBack = onBack,
    ) {
        when {
            !loaded && error != null -> RetryState(message = error!!, onRetry = { reloadKey++ })
            !loaded -> CenterMessage("Загрузка воркеров…")
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
                    item(key = "tabs") {
                        CapacityTabs(
                            selected = tab,
                            workerCount = currentWorkers!!.size,
                            subscriptionCount = currentSubscriptions!!.size,
                            onSelect = { tab = it },
                        )
                    }

                    error?.let { message ->
                        item(key = "error") {
                            Text(text = message, style = AppTheme.Label, color = AppTheme.Danger)
                        }
                    }

                    when (tab) {
                        CapacityTab.Workers -> {
                            if (currentWorkers!!.isEmpty()) {
                                item(key = "workers-empty") {
                                    EmptyNote("Ни один воркер не зарегистрирован.")
                                }
                            }
                            items(currentWorkers, key = { it.id }) { worker -> WorkerCard(worker) }
                        }

                        CapacityTab.Subscriptions -> {
                            if (currentSubscriptions!!.isEmpty()) {
                                item(key = "subs-empty") {
                                    EmptyNote(
                                        "Подписок нет: лимиты появятся, когда воркер впервые отчитается " +
                                            "об использовании харнесса.",
                                    )
                                }
                            }
                            items(currentSubscriptions, key = { it.id }) { subscription ->
                                SubscriptionCard(subscription)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The tab strip. Hand-rolled: this project depends on foundation only, there is no material3 here. */
@Composable
private fun CapacityTabs(
    selected: CapacityTab,
    workerCount: Int,
    subscriptionCount: Int,
    onSelect: (CapacityTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.Radius))
            .background(AppTheme.MenuBackground, RoundedCornerShape(AppTheme.Radius))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (entry in CapacityTab.entries) {
            val active = entry == selected
            val count = if (entry == CapacityTab.Workers) workerCount else subscriptionCount
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(AppTheme.Radius - 4.dp))
                    .background(
                        if (active) AppTheme.Background else AppTheme.MenuBackground,
                        RoundedCornerShape(AppTheme.Radius - 4.dp),
                    )
                    .clickable(role = Role.Tab, enabled = !active) { onSelect(entry) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${entry.label} ($count)",
                    style = AppTheme.Label,
                    color = if (active) AppTheme.Foreground else AppTheme.Muted,
                )
            }
        }
    }
}

/**
 * One worker: whether it is reachable at all, and then one block per harness it runs. A worker with
 * no harness bound to it is not broken — it can still take git-only jobs, which are never gated by a
 * limit — so it says that instead of showing an empty list.
 */
@Composable
private fun WorkerCard(worker: WorkerDto) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = worker.name,
                style = AppTheme.Subtitle,
                color = AppTheme.Foreground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            ContactBadge(worker.contactState, worker.status)
        }

        val meta = listOfNotNull(
            worker.hostname?.takeIf { it.isNotBlank() },
            worker.version?.takeIf { it.isNotBlank() },
            "до ${worker.maxConcurrentJobs} задач",
            formatTimestamp(worker.lastSeenAt)?.let { "виден $it" },
        )
        if (meta.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = meta.joinToString(" · "),
                style = AppTheme.Label,
                color = AppTheme.Muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (worker.harnesses.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Харнессы не привязаны — лимиты не применяются.",
                style = AppTheme.Body,
                color = AppTheme.Muted,
            )
            return@Card
        }

        for (harness in worker.harnesses) {
            Spacer(Modifier.height(16.dp))
            HarnessBlock(harness)
        }
    }
}

/** One harness of one worker: its gate state, whose subscription it spends, and its limit windows. */
@Composable
private fun HarnessBlock(harness: WorkerHarnessDto) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.Radius))
            .background(AppTheme.Background, RoundedCornerShape(AppTheme.Radius))
            .border(1.dp, AppTheme.Border, RoundedCornerShape(AppTheme.Radius))
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = harness.harnessKey,
                style = AppTheme.Body,
                color = AppTheme.Foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            HarnessStateBadge(harness.state)
        }

        val subscription = harness.subscription
        Spacer(Modifier.height(6.dp))
        Text(
            text = subscription?.let { "Подписка: ${it.name}" }
                // No binding to an account means nothing can close the gate for this harness: it is
                // worth saying outright, because the rows around it all show a limit.
                ?: "Подписка не привязана — лимит не отслеживается.",
            style = AppTheme.Label,
            color = AppTheme.Muted,
        )

        val jobs = listOfNotNull(
            "идёт ${harness.runningJobs}".takeIf { harness.runningJobs > 0 },
            "в очереди ${harness.queuedJobs}".takeIf { harness.queuedJobs > 0 },
            harness.maxConcurrent?.let { "не более $it" },
        )
        if (jobs.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(text = jobs.joinToString(" · "), style = AppTheme.Label, color = AppTheme.Muted)
        }

        WindowList(harness.windows, observedAt = harness.observedAt)

        subscription?.exhaustedUntil?.let { until ->
            Spacer(Modifier.height(10.dp))
            ExhaustedNote(until = until, reason = subscription.exhaustedReason)
        }

        // The worker reported usage for a different account than the subscription it is bound to.
        // Silently re-binding would move a whole team's gate onto one machine's login, so it is
        // shown and left to a person.
        if (harness.accountMismatch) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Отчёт пришёл от другого аккаунта, чем указан в подписке.",
                style = AppTheme.Label,
                color = AppTheme.Danger,
            )
        }
    }
}

/** One subscription: what it is, what is left of it, and which workers are spending it. */
@Composable
private fun SubscriptionCard(subscription: HarnessSubscriptionDto) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = subscription.name,
                style = AppTheme.Subtitle,
                color = AppTheme.Foreground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            HarnessStateBadge(if (subscription.exhausted) "exhausted" else "available")
        }

        val meta = listOfNotNull(
            subscription.provider?.takeIf { it.isNotBlank() },
            subscription.authKind?.takeIf { it.isNotBlank() },
            subscription.accountId?.takeIf { it.isNotBlank() },
            formatTimestamp(subscription.lastSignalAt)?.let { "отчёт $it" },
        )
        if (meta.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = meta.joinToString(" · "),
                style = AppTheme.Label,
                color = AppTheme.Muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        WindowList(subscription.windows, observedAt = subscription.lastSignalAt)

        subscription.exhaustedUntil?.let { until ->
            Spacer(Modifier.height(10.dp))
            ExhaustedNote(until = until, reason = subscription.exhaustedReason)
        }

        Spacer(Modifier.height(14.dp))
        SectionTitle("Воркеры (${subscription.workers.size})")
        if (subscription.workers.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Ни один воркер не привязан к этой подписке.",
                style = AppTheme.Label,
                color = AppTheme.Muted,
            )
        }
        for (worker in subscription.workers) {
            Spacer(Modifier.height(6.dp))
            SubscriptionWorkerRow(worker)
        }
    }
}

@Composable
private fun SubscriptionWorkerRow(worker: SubscriptionWorkerDto) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = listOfNotNull(worker.name, worker.harnessKey.takeIf { it.isNotBlank() })
                .joinToString(" · "),
            style = AppTheme.Body,
            color = if (worker.enabled) AppTheme.Foreground else AppTheme.Disabled,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        Text(
            text = listOfNotNull(
                contactLabel(worker.contactState),
                "идёт ${worker.runningJobs}".takeIf { worker.runningJobs > 0 },
            ).joinToString(" · "),
            style = AppTheme.Label,
            color = AppTheme.Muted,
        )
    }
}

/**
 * The limit windows themselves. Empty is a normal state and says so: it means nothing has reported
 * usage for this harness yet, which is different from "лимит не израсходован".
 */
@Composable
private fun WindowList(windows: List<HarnessWindowDto>, observedAt: String?) {
    Spacer(Modifier.height(12.dp))
    if (windows.isEmpty()) {
        Text(text = "Телеметрия лимитов ещё не приходила.", style = AppTheme.Label, color = AppTheme.Muted)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (window in windows) {
            WindowRow(window)
        }
    }
    // One timestamp for the whole block rather than per window: they all come from one report, and
    // stale numbers read as current ones without it.
    formatTimestamp(windows.firstOrNull { it.observedAt != null }?.observedAt ?: observedAt)?.let { at ->
        Spacer(Modifier.height(8.dp))
        Text(text = "Данные на $at", style = AppTheme.Label, color = AppTheme.Muted)
    }
}

/** One window as a labelled bar. A window without a percentage still shows its reset time. */
@Composable
private fun WindowRow(window: HarnessWindowDto) {
    val percent = window.usedPercent?.coerceIn(0.0, 100.0)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = window.label?.takeIf { it.isNotBlank() } ?: window.key,
                style = AppTheme.Label,
                color = AppTheme.Foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            Text(
                text = percent?.let { "${it.toInt()}%" } ?: "нет данных",
                style = AppTheme.Label,
                color = if (percent == null) AppTheme.Muted else usageColor(percent),
            )
        }
        if (percent != null) {
            Spacer(Modifier.height(6.dp))
            UsageBar(percent)
        }
        window.resetsAt?.let { resetsAt ->
            formatTimestamp(resetsAt)?.let { at ->
                Spacer(Modifier.height(4.dp))
                val left = formatRemaining(resetsAt)?.let { " (осталось $it)" } ?: ""
                Text(text = "Обновится $at$left", style = AppTheme.Label, color = AppTheme.Muted)
            }
        }
    }
}

/** The bar itself: a full-width track with the used fraction drawn over it. */
@Composable
private fun UsageBar(percent: Double) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(AppTheme.Border, RoundedCornerShape(999.dp)),
    ) {
        // `fillMaxWidth(fraction)` rather than a measured width: the track is already as wide as the
        // card, so the fraction is the bar.
        Box(
            modifier = Modifier
                .fillMaxWidth((percent / 100.0).toFloat().coerceIn(0f, 1f))
                .height(6.dp)
                .background(usageColor(percent), RoundedCornerShape(999.dp)),
        )
    }
}

/**
 * Colour of a usage number. Thresholds are the app's own reading aid and decide nothing: whether a
 * worker may take a job is `state`, which the server sends — a subscription can be closed at 40% by
 * a `stopPolicy` or by a refusal, and open at 95%.
 */
private fun usageColor(percent: Double) = when {
    percent >= 90.0 -> AppTheme.Danger
    percent >= 70.0 -> AppTheme.Warning
    else -> AppTheme.Primary
}

@Composable
private fun ExhaustedNote(until: String, reason: String?) {
    Text(
        text = listOfNotNull(
            formatTimestamp(until)?.let {
                val left = formatRemaining(until)?.let { left -> " (осталось $left)" } ?: ""
                "Лимит закрыт до $it$left"
            } ?: "Лимит закрыт",
            reason?.takeIf { it.isNotBlank() },
        ).joinToString(" — "),
        style = AppTheme.Label,
        color = AppTheme.Danger,
    )
}

/** `available` / `exhausted` / `disabled`, exactly as the server decided it. */
@Composable
private fun HarnessStateBadge(state: String) {
    val (label, color) = when (state) {
        "available" -> "доступен" to AppTheme.Primary
        "exhausted" -> "лимит исчерпан" to AppTheme.Danger
        "disabled" -> "выключен" to AppTheme.Disabled
        else -> state to AppTheme.Muted
    }
    Badge(label, color)
}

/**
 * Whether the machine is talking to the server at all. A revoked or paused worker is shown by its
 * `status` instead: it is offline on purpose, and "офлайн" would read as a fault.
 */
@Composable
private fun ContactBadge(contactState: String, status: String) {
    if (status != "active") {
        Badge(
            when (status) {
                "paused" -> "на паузе"
                "revoked" -> "отозван"
                "pending" -> "не подключён"
                else -> status
            },
            AppTheme.Disabled,
        )
        return
    }
    val color = if (contactState == "online") AppTheme.Primary else AppTheme.Disabled
    Badge(contactLabel(contactState), color)
}

private fun contactLabel(contactState: String) = when (contactState) {
    "online" -> "на связи"
    "offline" -> "офлайн"
    else -> "ни разу не выходил на связь"
}

@Composable
private fun Badge(label: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = label,
        style = AppTheme.Label,
        color = AppTheme.PrimaryForeground,
        modifier = Modifier
            .background(color, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** The card both tabs are built out of — same frame as the run board's rows. */
@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.Radius))
            .border(1.dp, AppTheme.Border, RoundedCornerShape(AppTheme.Radius))
            .background(AppTheme.Surface, RoundedCornerShape(AppTheme.Radius))
            .padding(20.dp),
        content = content,
    )
}

@Composable
private fun EmptyNote(text: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(text = text, style = AppTheme.Body, color = AppTheme.Muted)
    }
}
