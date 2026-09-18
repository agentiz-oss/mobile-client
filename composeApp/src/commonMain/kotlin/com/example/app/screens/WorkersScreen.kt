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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.example.app.components.AppScaffold
import com.example.app.components.KeyIcon
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
import com.example.app.i18n.strings
import com.example.app.theme.AppTheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * How often the page re-reads. Usage is reported by each worker every 120 s, so anything faster
 * only re-renders the same numbers — this is slow enough to be cheap and fast enough that a limit
 * that just closed shows up while the user is still looking at the screen.
 */
private const val CAPACITY_POLL_MS = 30_000L

/** The two halves of the page. Kept as a type so the tab strip and the body cannot disagree. */
private enum class CapacityTab {
    Workers,
    Subscriptions,
    ;

    /** Resolved when the strip renders rather than stored on the constant: a language can change
     *  while the screen is open, and an enum's constructor argument would be frozen at class init. */
    val label: String get() = when (this) {
        Workers -> strings.workersTabWorkers
        Subscriptions -> strings.workersTabSubscriptions
    }
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
    // Old, paused and disconnected records are valuable for diagnosis but should not hide the
    // machines that can actually claim work. A person can still reveal the whole fleet in one tap.
    var showInactiveWorkers by remember { mutableStateOf(false) }

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
            error = strings.networkError(e.message)
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
        title = strings.workersTitle,
        // What is wrong, in one line, with the thing a person can actually act on first: a login is
        // theirs to fix, an exhausted quota fixes itself.
        subtitle = listOfNotNull(
            currentWorkers?.count { it.needsLogin }?.takeIf { it > 0 }?.let(strings::workersNeedLogin),
            currentSubscriptions?.count { it.exhausted }?.takeIf { it > 0 }
                ?.let(strings::workersSubscriptionsExhausted),
        ).takeIf { it.isNotEmpty() }?.joinToString(" · "),
        menu = menu,
        onOpenSettings = onOpenSettings,
        onOpenProfile = onOpenProfile,
        onBack = onBack,
    ) {
        when {
            !loaded && error != null -> RetryState(message = error!!, onRetry = { reloadKey++ })
            !loaded -> CenterMessage(strings.workersLoading)
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
                            // "Inactive" means nobody expects work from it — paused, revoked, never
                            // connected. A machine that is *supposed* to be working and is not is
                            // the opposite of that and is promoted into the default list: hiding it
                            // behind a toggle is how a broken fleet looks like an empty one.
                            val hidden = currentWorkers!!.filter { isInactiveWorker(it) && !isBrokenWorker(it) }
                            val visible = (if (showInactiveWorkers) currentWorkers!! else currentWorkers!! - hidden.toSet())
                                // Broken first: the list is otherwise alphabetical, and the one row
                                // a person opened this screen for must not be the last one.
                                .sortedBy { !isBrokenWorker(it) }
                            val inactive = hidden
                            if (inactive.isNotEmpty()) {
                                item(key = "inactive-toggle") {
                                    InactiveWorkersToggle(
                                        count = inactive.size,
                                        expanded = showInactiveWorkers,
                                        onClick = { showInactiveWorkers = !showInactiveWorkers },
                                    )
                                }
                            }
                            if (visible.isEmpty()) {
                                item(key = "workers-empty") {
                                    EmptyNote(
                                        if (currentWorkers!!.isEmpty()) strings.workersNoneRegistered
                                        else strings.workersNoneAvailable,
                                    )
                                }
                            }
                            items(visible, key = { it.id }) { worker -> WorkerCard(worker) }
                        }

                        CapacityTab.Subscriptions -> {
                            if (currentSubscriptions!!.isEmpty()) {
                                item(key = "subs-empty") {
                                    EmptyNote(
                                        strings.workersNoSubscriptions,
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
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                // A machine can be perfectly reachable and still unable to do any work, and the
                // card is tall enough that the harness block saying so is below the fold. Without
                // this the header reads "на связи" and nothing else — which is exactly how a
                // logged-out worker looked healthy for a day.
                if (worker.needsLogin) Badge(strings.workerNeedsLogin, AppTheme.Danger)
                ContactBadge(worker.contactState, worker.status)
            }
        }

        val meta = listOfNotNull(
            worker.hostname?.takeIf { it.isNotBlank() },
            worker.version?.takeIf { it.isNotBlank() },
            strings.workerMaxJobs(worker.maxConcurrentJobs),
            formatTimestamp(worker.lastSeenAt)?.let(strings::workerLastSeen),
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
                text = strings.workerNoHarnesses,
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
            text = subscription?.let { strings.workerSubscription(it.name) }
                // No binding to an account means nothing can close the gate for this harness: it is
                // worth saying outright, because the rows around it all show a limit.
                ?: strings.workerSubscriptionUnbound,
            style = AppTheme.Label,
            color = AppTheme.Muted,
        )

        val jobs = listOfNotNull(
            strings.workerRunningJobs(harness.runningJobs).takeIf { harness.runningJobs > 0 },
            strings.workerQueuedJobs(harness.queuedJobs).takeIf { harness.queuedJobs > 0 },
            harness.maxConcurrent?.let(strings::workerMaxConcurrent),
        )
        if (jobs.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(text = jobs.joinToString(" · "), style = AppTheme.Label, color = AppTheme.Muted)
        }

        if (harness.authState == "expired") NeedsLoginNote(harness)

        WindowList(harness.windows, observedAt = harness.observedAt)

        SubscriptionIdleNote(subscription?.lastLimitChangeAt)

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
                text = strings.workerAccountMismatch,
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
            formatTimestamp(subscription.lastSignalAt)?.let(strings::workerReportedAt),
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

        SubscriptionIdleNote(subscription.lastLimitChangeAt)

        subscription.exhaustedUntil?.let { until ->
            Spacer(Modifier.height(10.dp))
            ExhaustedNote(until = until, reason = subscription.exhaustedReason)
        }

        Spacer(Modifier.height(14.dp))
        SectionTitle(strings.subscriptionWorkers(subscription.workers.size))
        if (subscription.workers.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = strings.subscriptionNoWorkers,
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
                // First and in red: from the subscription's side this is the machine that stopped
                // spending it, and "на связи" alone would say the opposite.
                strings.workerNeedsLogin.takeIf { worker.authState == "expired" },
                contactLabel(worker.contactState),
                strings.workerRunningJobs(worker.runningJobs).takeIf { worker.runningJobs > 0 },
            ).joinToString(" · "),
            style = AppTheme.Label,
            color = if (worker.authState == "expired") AppTheme.Danger else AppTheme.Muted,
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
        Text(text = strings.subscriptionNoTelemetry, style = AppTheme.Label, color = AppTheme.Muted)
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
        Text(text = strings.subscriptionDataAt(at), style = AppTheme.Label, color = AppTheme.Muted)
    }
}

/**
 * One window as a labelled bar. A window without a percentage still shows its reset time.
 *
 * Which half of the quota the number states is the provider's decision and arrives with the window
 * (`meter`): Claude reports «израсходовано», Codex «осталось». The bar always fills with the number
 * printed beside it, while the colour follows the *spent* share either way — a nearly empty
 * "осталось" bar has to read as the alarming one.
 */
@Composable
private fun WindowRow(window: HarnessWindowDto) {
    val used = window.usedPercent?.coerceIn(0.0, 100.0)
    val remaining = window.meter == "remaining"
    // Rounded once, so the bar and the number beside it cannot disagree.
    val shown = used?.let { (if (remaining) 100.0 - it else it).roundToInt() }
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
                text = shown?.let {
                    if (remaining) strings.subscriptionRemainingPercent(it)
                    else strings.subscriptionUsedPercent(it)
                } ?: strings.subscriptionNoData,
                style = AppTheme.Label,
                color = if (used == null) AppTheme.Muted else usageColor(used),
            )
        }
        if (used != null && shown != null) {
            Spacer(Modifier.height(6.dp))
            UsageBar(shown.toDouble(), usageColor(used))
        }
        window.resetsAt?.let { resetsAt ->
            formatTimestamp(resetsAt)?.let { at ->
                Spacer(Modifier.height(4.dp))
                // A long window — the weekly one in practice — also says how many whole session
                // windows are left in it: «осталось 154 ч 12 мин» is not a number anyone can plan
                // against, and the sessions left in it is what that figure gets read for.
                val left = formatRemaining(resetsAt)?.let { until ->
                    val sessions = formatFullSessionWindows(resetsAt, window.sessionWindowMinutes)
                        ?.let(strings::subscriptionAlsoSessions) ?: ""
                    strings.subscriptionResetLeft(until, sessions)
                } ?: ""
                Text(
                    text = strings.subscriptionResetAt(at, left),
                    style = AppTheme.Label,
                    color = AppTheme.Muted,
                )
            }
        }
    }
}

/** The bar itself: a full-width track with the shown fraction drawn over it, in the given colour. */
@Composable
private fun UsageBar(percent: Double, color: Color) {
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
                .background(color, RoundedCornerShape(999.dp)),
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

/** The user-facing idle clock is based on quota changes, never on the report freshness timestamp. */
@Composable
private fun SubscriptionIdleNote(lastLimitChangeAt: String?) {
    if (lastLimitChangeAt == null) return
    val idle = formatWaiting(lastLimitChangeAt) ?: strings.subscriptionJustNow
    Spacer(Modifier.height(8.dp))
    Text(text = strings.subscriptionIdle(idle), style = AppTheme.Label, color = AppTheme.Muted)
}

@Composable
private fun InactiveWorkersToggle(count: Int, expanded: Boolean, onClick: () -> Unit) {
    Text(
        text = if (expanded) strings.workersHideInactive(count) else strings.workersShowInactive(count),
        style = AppTheme.Label,
        color = AppTheme.Foreground,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.Radius))
            .border(1.dp, AppTheme.Border, RoundedCornerShape(AppTheme.Radius))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
    )
}

/** Only active workers that are currently talking to the server belong in the default fleet view. */
internal fun isInactiveWorker(worker: WorkerDto): Boolean =
    worker.status != "active" || worker.contactState != "online"

/**
 * "Эта машина ни одной задачи этого типа сейчас не возьмёт" — any harness on it is logged out.
 *
 * Read from the server's own per-harness state rather than re-derived from percentages or from the
 * contact state: a logged-out worker keeps polling and keeps looking alive, which is the whole
 * reason this is a separate thing to show.
 */
internal val WorkerDto.needsLogin: Boolean
    get() = harnesses.any { it.authState == "expired" }

/**
 * A machine somebody has to look at: it is switched on for work and either cannot log in or has
 * stopped answering at all. These are the two states the drawer counts, so what the badge says and
 * what this screen shows cannot come apart.
 *
 * `never_contacted` is deliberately not one of them — a worker that has never connected is an
 * unfinished setup, not a breakage.
 */
internal fun isBrokenWorker(worker: WorkerDto): Boolean =
    worker.status == "active" && (worker.contactState == "offline" || worker.needsLogin)

@Composable
private fun ExhaustedNote(until: String, reason: String?) {
    Text(
        text = listOfNotNull(
            formatTimestamp(until)?.let {
                val left = formatRemaining(until)?.let(strings::remainingSuffix) ?: ""
                strings.workerLimitUntil(it, left)
            } ?: strings.workerLimitClosed,
            reason?.takeIf { it.isNotBlank() },
        ).joinToString(" — "),
        style = AppTheme.Label,
        color = AppTheme.Danger,
    )
}

/** `available` / `exhausted` / `unauthorized` / `disabled`, exactly as the server decided it. */
@Composable
private fun HarnessStateBadge(state: String) {
    val color = when (state) {
        "available" -> AppTheme.Primary
        "exhausted" -> AppTheme.Danger
        // Not a limit: nothing here ends on a clock, which is why it is worded as a demand and not
        // as a state («нужен вход», not «нет авторизации»).
        "unauthorized" -> AppTheme.Danger
        "disabled" -> AppTheme.Disabled
        else -> AppTheme.Muted
    }
    Badge(strings.subscriptionState(state) ?: state, color)
}

/**
 * What to do about a machine nobody is logged into any more — the one state on this screen that
 * cannot be fixed from the phone, and therefore the one that has to say where it *can* be fixed.
 *
 * Sits above the limit bars rather than below them: with no credential the numbers next to it are
 * frozen at whatever the last working report said, and reading them as current is exactly the
 * mistake that made this outage take a day to spot.
 */
@Composable
private fun NeedsLoginNote(harness: WorkerHarnessDto) {
    val since = formatTimestamp(harness.authFailedSince)
    Spacer(Modifier.height(12.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.Radius))
            .background(AppTheme.DangerSubtle, RoundedCornerShape(AppTheme.Radius))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyIcon(AppTheme.Danger, size = 16.dp)
            Text(
                text = since?.let(strings::workerAuthEndedAt) ?: strings.workerAuthEnded,
                style = AppTheme.Body,
                color = AppTheme.Danger,
            )
        }
        harness.authDetail?.takeIf { it.isNotBlank() }?.let { detail ->
            Spacer(Modifier.height(4.dp))
            Text(text = detail, style = AppTheme.Label, color = AppTheme.Muted)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (harness.harnessKey == "claude") strings.workerAuthFixClaude
            else strings.workerAuthFixOther,
            style = AppTheme.Label,
            color = AppTheme.Foreground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = strings.workerAuthQueueNote,
            style = AppTheme.Label,
            color = AppTheme.Muted,
        )
    }
}

/**
 * Whether the machine is talking to the server at all. A revoked or paused worker is shown by its
 * `status` instead: it is offline on purpose, and "офлайн" would read as a fault.
 */
@Composable
private fun ContactBadge(contactState: String, status: String) {
    if (status != "active") {
        Badge(
            strings.workerStatusWord(status) ?: status,
            AppTheme.Disabled,
        )
        return
    }
    // Red rather than grey for an *active* machine that stopped answering: the operator said this
    // one should be taking work, so its silence is a fault and not a setting. A paused or revoked
    // worker never reaches here — it is drawn by its status above.
    val color = when (contactState) {
        "online" -> AppTheme.Primary
        "offline" -> AppTheme.Danger
        else -> AppTheme.Disabled
    }
    Badge(contactLabel(contactState), color)
}

private fun contactLabel(contactState: String) = strings.workerContactState(contactState)

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
