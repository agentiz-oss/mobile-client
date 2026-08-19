package com.example.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.example.app.components.AppScaffold
import com.example.app.components.BellIcon
import com.example.app.components.DownIcon
import com.example.app.components.ForwardIcon
import com.example.app.components.MenuEntry
import com.example.app.data.AgentizApi
import com.example.app.data.ApiException
import com.example.app.data.NotificationPolicyDoc
import com.example.app.data.NotificationPolicyDto
import com.example.app.data.ProjectDto
import com.example.app.data.Session
import com.example.app.theme.AppTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 * Notifications: which events reach this owner's phone and the dashboard bell.
 *
 * Laid out as scopes rather than as one list of switches — общие правила first, then a card per
 * project — because that is the shape of the policy itself: a project either follows the general
 * rules or overrides them, and a flat list hides which of the two is happening. Every card says
 * where it stands before it is opened (иконка + сводка), so the screen answers "почему тихо"
 * without expanding anything.
 *
 * Pipeline-level entries exist server-side but are edited from the dashboard/MCP — the app has no
 * pipeline screens to hang them on yet.
 *
 * Every change saves immediately (the server merges only this owner's entries); the feed itself is
 * written whatever is switched off here, so nothing is ever lost — only quiet.
 */
@Composable
fun NotificationsScreen(
    session: Session,
    menu: List<MenuEntry>,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val api = remember(session.serverUrl) { AgentizApi(session.serverUrl) }
    DisposableEffect(api) { onDispose { api.close() } }

    var policy by remember { mutableStateOf<NotificationPolicyDto?>(null) }
    var projects by remember { mutableStateOf<List<ProjectDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reloadKey) {
        try {
            policy = api.notificationPolicy(session.token)
            projects = api.projects(session.token)
            error = null
        } catch (e: ApiException) {
            error = e.message
        } catch (e: Throwable) {
            error = "Ошибка сети: ${e.message ?: "неизвестная ошибка"}"
        }
    }

    /**
     * Optimistic save: the matrix updates instantly, the PUT trails it. On failure the server copy
     * is reloaded — showing a state the server refused would make every later tap build on a lie.
     */
    fun save(defaults: JsonObject, projectScopes: JsonObject) {
        val current = policy ?: return
        policy = current.copy(defaults = defaults, projects = projectScopes)
        saving = true
        scope.launch {
            try {
                policy = api.updateNotificationPolicy(session.token, defaults = defaults, projects = projectScopes)
                error = null
            } catch (e: ApiException) {
                error = e.message
                reloadKey++
            } catch (e: Throwable) {
                error = "Ошибка сети: ${e.message ?: "неизвестная ошибка"}"
                reloadKey++
            } finally {
                saving = false
            }
        }
    }

    val current = policy
    AppScaffold(
        title = "Уведомления",
        subtitle = if (saving) "Сохраняется…" else null,
        menu = menu,
        onOpenSettings = onOpenSettings,
        onOpenProfile = onOpenProfile,
        onBack = onBack,
    ) {
        when {
            current == null && error != null -> RetryState(message = error!!, onRetry = { reloadKey++ })
            current == null -> CenterMessage("Загрузка настроек…")
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                error?.let { Text(text = it, style = AppTheme.Label, color = AppTheme.Danger) }
                if (current.shadowedByEnvironment) {
                    Text(
                        text = "Политика задана переменной окружения на сервере — правки отсюда сохранятся, но не подействуют, пока переменную не уберут.",
                        style = AppTheme.Label,
                        color = AppTheme.Danger,
                    )
                }

                Text(
                    text = "Лента активностей пишется всегда; здесь выключается только доставка — пуш на телефон и колокольчик в панели.",
                    style = AppTheme.Label,
                    color = AppTheme.Muted,
                )

                SectionTitle("Общие правила")
                ScopeCard(
                    title = "Для всех проектов",
                    summary = scopeSummary(current.defaults, muteLabel = "всё отключено", emptyLabel = "по умолчанию"),
                    muted = NotificationPolicyDoc.isMuted(current.defaults),
                ) {
                    PolicyMatrix(
                        policy = current,
                        scope = current.defaults,
                        inherited = null,
                        onChange = { updated -> save(updated, current.projects) },
                    )
                }

                Spacer(Modifier.height(4.dp))
                SectionTitle("Проекты")
                if (projects.isEmpty()) {
                    Text(
                        text = "Проектов пока нет — настраивать нечего.",
                        style = AppTheme.Label,
                        color = AppTheme.Muted,
                    )
                } else {
                    projects.forEach { project ->
                        val projectScope = NotificationPolicyDoc.scopeOf(current.projects, project.id)
                        val muted = NotificationPolicyDoc.isMuted(projectScope)
                        ScopeCard(
                            title = project.name,
                            summary = scopeSummary(projectScope, muteLabel = "отключены", emptyLabel = "как в общих"),
                            muted = muted,
                        ) {
                            MuteRow(
                                muted = muted,
                                onToggle = {
                                    save(
                                        current.defaults,
                                        NotificationPolicyDoc.withScope(
                                            current.projects,
                                            project.id,
                                            NotificationPolicyDoc.withMute(projectScope, !muted),
                                        ),
                                    )
                                },
                            )
                            Spacer(Modifier.height(10.dp))
                            PolicyMatrix(
                                policy = current,
                                scope = projectScope,
                                inherited = listOf(current.defaults),
                                onChange = { updatedScope ->
                                    save(current.defaults, NotificationPolicyDoc.withScope(current.projects, project.id, updatedScope))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** What a scope says, in one line — read before anything is expanded. */
private fun scopeSummary(scope: JsonObject, muteLabel: String, emptyLabel: String): String {
    val rules = scope.keys.count { it != "mute" }
    val muted = NotificationPolicyDoc.isMuted(scope)
    return when {
        muted && rules > 0 -> "$muteLabel, кроме $rules"
        muted -> muteLabel
        rules > 0 -> "своих правил: $rules"
        else -> emptyLabel
    }
}

/**
 * One scope as a card: a bell that already shows whether it is silenced, the name, the one-line
 * summary, and the rules themselves behind a tap. Collapsed by default — with a project each for a
 * dozen projects, an always-open matrix turns the screen into a wall of switches nobody scrolls.
 */
@Composable
private fun ScopeCard(
    title: String,
    summary: String,
    muted: Boolean,
    content: @Composable () -> Unit,
) {
    var expanded by remember(title) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AppTheme.Border, RoundedCornerShape(AppTheme.Radius))
            .background(AppTheme.Surface, RoundedCornerShape(AppTheme.Radius))
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(AppTheme.Background, CircleShape)
                    .border(1.dp, AppTheme.Border, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                BellIcon(tint = if (muted) AppTheme.Danger else AppTheme.Muted, size = 18.dp, muted = muted)
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = AppTheme.Subtitle,
                    color = AppTheme.Foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = summary,
                    style = AppTheme.Label,
                    color = if (muted) AppTheme.Danger else AppTheme.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (expanded) DownIcon(AppTheme.Muted, size = 16.dp) else ForwardIcon(AppTheme.Muted, size = 16.dp)
        }
        if (expanded) {
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/** The project-wide switch, as its own row above the per-type rules it overrides. */
@Composable
private fun MuteRow(muted: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(text = "Отключить всё по проекту", style = AppTheme.Body, color = AppTheme.Foreground)
            Text(
                text = "Кроме типов, для которых ниже выбрано своё значение",
                style = AppTheme.Label,
                color = AppTheme.Muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PolicyChip(
            prefix = "",
            label = if (muted) "отключены" else "включены",
            emphasized = muted,
            onClick = onToggle,
        )
    }
}

/** on → silent → off → on for push; on → off → on for the bell; overrides add "как в общих" to the cycle. */
private fun nextValue(currentValue: String?, channel: String, allowInherit: Boolean): String? {
    val cycle = if (channel == "push") listOf("on", "silent", "off") else listOf("on", "off")
    val position = cycle.indexOf(currentValue)
    return when {
        currentValue == null -> cycle.first()
        position == cycle.lastIndex -> if (allowInherit) null else cycle.first()
        else -> cycle[position + 1]
    }
}

private fun labelFor(value: String?): String = when (value) {
    "on" -> "вкл"
    "silent" -> "тихо"
    "off" -> "выкл"
    else -> "как в общих"
}

/**
 * The type × channel matrix of one scope. For `defaults` ([inherited] == null) an unset cell shows
 * the built-in value and tapping writes an explicit one; for a project scope an unset cell reads
 * "как в общих" and the cycle returns to it, expressed as removing the override.
 */
@Composable
private fun PolicyMatrix(
    policy: NotificationPolicyDto,
    scope: JsonObject,
    inherited: List<JsonObject>?,
    onChange: (JsonObject) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        policy.types.forEach { type ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = type.label.ifBlank { type.type },
                    style = AppTheme.Body,
                    color = AppTheme.Foreground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                listOf("push", "dashboard").forEach { channel ->
                    val explicit = NotificationPolicyDoc.channel(scope, type.type, channel)
                    val builtin = policy.builtinDefaults[type.type]
                        ?.let { if (channel == "push") it.push else it.dashboard } ?: "on"
                    // Defaults scope shows the value in force (built-in until overridden); a project
                    // scope shows only its own override and reads "как в общих" without one.
                    val shown = explicit ?: builtin.takeIf { inherited == null }
                    PolicyChip(
                        prefix = if (channel == "push") "push" else "bell",
                        label = labelFor(shown),
                        emphasized = explicit != null,
                        onClick = {
                            val next = nextValue(shown, channel, allowInherit = inherited != null)
                            onChange(NotificationPolicyDoc.withChannel(scope, type.type, channel, next))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PolicyChip(prefix: String, label: String, emphasized: Boolean, onClick: () -> Unit) {
    Text(
        text = if (prefix.isEmpty()) label else "$prefix: $label",
        style = AppTheme.Label,
        color = if (emphasized) AppTheme.PrimaryForeground else AppTheme.Muted,
        modifier = Modifier
            .padding(start = 6.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .background(
                if (emphasized) AppTheme.Primary else AppTheme.Surface,
                RoundedCornerShape(999.dp),
            )
            .border(1.dp, AppTheme.Border, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
