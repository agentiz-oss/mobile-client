package com.example.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
 * Settings — today that means notifications: which events reach this owner's phone and the
 * dashboard bell. The matrix edits the policy's `defaults` scope; each project below adds its own
 * «отключить всё» switch and per-type overrides ("как в общих" = no override). Pipeline-level
 * entries exist server-side but are edited from the dashboard/MCP — the app has no pipeline
 * screens to hang them on yet.
 *
 * Every change saves immediately (the server merges only this owner's entries); the feed itself is
 * written whatever is switched off here, so nothing is ever lost — only quiet.
 */
@Composable
fun SettingsScreen(
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
        title = "Настройки",
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

                SectionTitle("Уведомления · общие")
                Text(
                    text = "Лента активностей пишется всегда; здесь выключается только доставка.",
                    style = AppTheme.Label,
                    color = AppTheme.Muted,
                )
                PolicyMatrix(
                    policy = current,
                    scope = current.defaults,
                    inherited = null,
                    onChange = { updated -> save(updated, current.projects) },
                )

                if (projects.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    SectionTitle("Проекты")
                    projects.forEach { project ->
                        ProjectPolicyCard(
                            policy = current,
                            project = project,
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
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AppTheme.Border, RoundedCornerShape(AppTheme.Radius))
            .background(AppTheme.Surface, RoundedCornerShape(AppTheme.Radius))
            .padding(16.dp),
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
        text = "$prefix: $label",
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

/** One project: the mute switch on top, per-type overrides expandable below it. */
@Composable
private fun ProjectPolicyCard(
    policy: NotificationPolicyDto,
    project: ProjectDto,
    onChange: (JsonObject) -> Unit,
) {
    val scope = NotificationPolicyDoc.scopeOf(policy.projects, project.id)
    val muted = NotificationPolicyDoc.isMuted(scope)
    var expanded by remember(project.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
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
                text = project.name,
                style = AppTheme.Subtitle,
                color = AppTheme.Foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            PolicyChip(
                prefix = "уведомления",
                label = if (muted) "отключены" else "включены",
                emphasized = muted,
                onClick = { onChange(NotificationPolicyDoc.withMute(scope, !muted)) },
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (expanded) "Скрыть переопределения" else "Переопределения по типам…",
            style = AppTheme.Label,
            color = AppTheme.Muted,
            modifier = Modifier.clickable(role = Role.Button) { expanded = !expanded },
        )
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            PolicyMatrix(
                policy = policy,
                scope = scope,
                inherited = listOf(policy.defaults),
                onChange = onChange,
            )
        }
    }
}
