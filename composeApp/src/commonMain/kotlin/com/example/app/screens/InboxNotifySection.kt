package com.example.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.example.app.components.BellIcon
import com.example.app.data.AgentizApi
import com.example.app.data.ApiException
import com.example.app.data.InboxItemDto
import com.example.app.data.NotificationPolicyDoc
import com.example.app.data.NotificationPolicyDto
import com.example.app.data.Session
import com.example.app.theme.AppTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 * «Почему это пришло и как сделать, чтобы не приходило» — the notification rules behind one inbox
 * row, edited where the row is read.
 *
 * The inbox and the уведомления screen are two views of one thing: a row is here because an event
 * of some type was recorded, and whether that event also woke anybody is the policy's decision.
 * Asking "перестань мне писать про это" at the moment of annoyance is the only time anybody asks
 * it, and walking to a settings screen to hunt for the right type in the right project is exactly
 * where that intention dies — so the three rules that could possibly apply to this row are here,
 * named by the row itself.
 *
 * What it can express is what the policy can express, no more: delivery is per **type per scope**,
 * never per row. Silencing from here silences every future event of the same type in the scope
 * chosen, which is why each switch says whose rule it is writing. The row itself does not go away
 * — the feed and the inbox are written regardless of delivery, and hiding *this* row is the other
 * gesture ([InboxItemDto.dismissible]).
 */
@Composable
fun InboxNotifySection(
    session: Session,
    item: InboxItemDto,
    /** Called after a successful write, so the row's own «пуш …» line is refetched. */
    onChanged: () -> Unit,
    /** The whole matrix, for the rules this panel deliberately does not show (bell, other types). */
    onOpenAllSettings: (() -> Unit)? = null,
) {
    val api = remember(session.serverUrl) { AgentizApi(session.serverUrl) }
    DisposableEffect(api) { onDispose { api.close() } }
    val scope = rememberCoroutineScope()

    var policy by remember { mutableStateOf<NotificationPolicyDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(item.id) {
        try {
            policy = api.notificationPolicy(session.token)
            error = null
        } catch (e: ApiException) {
            error = e.message
        } catch (e: Throwable) {
            error = "Ошибка сети: ${e.message ?: "неизвестная ошибка"}"
        }
    }

    /**
     * Writes one scope map back. Only the map that changed is sent: the server leaves an omitted
     * one exactly as stored, so editing a project rule from here cannot disturb pipeline rules
     * this panel never showed.
     */
    fun save(projects: JsonObject? = null, pipelines: JsonObject? = null) {
        if (saving) return
        saving = true
        scope.launch {
            try {
                policy = api.updateNotificationPolicy(session.token, projects = projects, pipelines = pipelines)
                error = null
                onChanged()
            } catch (e: ApiException) {
                error = e.message
            } catch (e: Throwable) {
                error = "Ошибка сети: ${e.message ?: "неизвестная ошибка"}"
            } finally {
                saving = false
            }
        }
    }

    val notify = item.notify
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AppTheme.Border, RoundedCornerShape(AppTheme.Radius))
            .background(AppTheme.Surface, RoundedCornerShape(AppTheme.Radius))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BellIcon(
                tint = if (notify?.push == "off") AppTheme.Danger else AppTheme.Muted,
                size = 16.dp,
                muted = notify?.push == "off",
            )
            Text(
                text = notify?.typeLabel?.ifBlank { item.badge } ?: item.badge,
                style = AppTheme.Subtitle,
                color = AppTheme.Foreground,
            )
        }
        notify?.label?.takeIf { it.isNotBlank() }?.let { line ->
            Spacer(Modifier.height(4.dp))
            Text(text = "Сейчас: $line", style = AppTheme.Label, color = AppTheme.Muted)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Настройка выключает только доставку — пуш на телефон. Строка во входящих и запись"
                + " в ленте появятся в любом случае.",
            style = AppTheme.Footnote,
            color = AppTheme.Muted,
        )

        error?.let { message ->
            Spacer(Modifier.height(8.dp))
            Text(text = message, style = AppTheme.Label, color = AppTheme.Danger)
        }

        val current = policy
        Spacer(Modifier.height(12.dp))
        when {
            current == null && error != null -> Unit
            current == null -> Text(text = "Загрузка настроек…", style = AppTheme.Label, color = AppTheme.Muted)
            else -> {
                val projectScope = NotificationPolicyDoc.scopeOf(current.projects, item.projectId)
                val projectMuted = NotificationPolicyDoc.isMuted(projectScope)

                // The narrowest rule first: this kind of event, in this project. It is the one a
                // reader actually means by "перестань про это писать".
                NotifyRule(
                    title = "Такие уведомления в проекте «${item.projectName ?: "без имени"}»",
                    subtitle = "Только этот тип события. Остальное по проекту не меняется.",
                    value = NotificationPolicyDoc.channel(projectScope, item.activityType, "push"),
                    enabled = !saving,
                    onSelect = { value ->
                        save(
                            projects = NotificationPolicyDoc.withScope(
                                current.projects,
                                item.projectId,
                                NotificationPolicyDoc.withChannel(projectScope, item.activityType, "push", value),
                            ),
                        )
                    },
                )

                item.notify?.pipelineSpecId?.let { pipelineId ->
                    val pipelineScope = NotificationPolicyDoc.scopeOf(current.pipelines, pipelineId)
                    Spacer(Modifier.height(12.dp))
                    NotifyRule(
                        title = "Только для этого пайплайна",
                        subtitle = "Перебивает правило проекта — например «проект молчит, а релиз пишет».",
                        value = NotificationPolicyDoc.channel(pipelineScope, item.activityType, "push"),
                        enabled = !saving,
                        onSelect = { value ->
                            save(
                                pipelines = NotificationPolicyDoc.withScope(
                                    current.pipelines,
                                    pipelineId,
                                    NotificationPolicyDoc.withChannel(pipelineScope, item.activityType, "push", value),
                                ),
                            )
                        },
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Switch, enabled = !saving) {
                            save(
                                projects = NotificationPolicyDoc.withScope(
                                    current.projects,
                                    item.projectId,
                                    NotificationPolicyDoc.withMute(projectScope, !projectMuted),
                                ),
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "Ничего не присылать по этому проекту",
                            style = AppTheme.Body,
                            color = AppTheme.Foreground,
                        )
                        Text(
                            text = "Кроме типов, для которых выбрано своё значение",
                            style = AppTheme.Footnote,
                            color = AppTheme.Muted,
                        )
                    }
                    NotifyChip(
                        label = if (projectMuted) "выключено" else "включено",
                        selected = projectMuted,
                        enabled = !saving,
                        onClick = {
                            save(
                                projects = NotificationPolicyDoc.withScope(
                                    current.projects,
                                    item.projectId,
                                    NotificationPolicyDoc.withMute(projectScope, !projectMuted),
                                ),
                            )
                        },
                    )
                }

                if (current.shadowedByEnvironment) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "На сервере политика задана переменной окружения — правки сохранятся, но"
                            + " не подействуют, пока её не уберут.",
                        style = AppTheme.Footnote,
                        color = AppTheme.Danger,
                    )
                }

                onOpenAllSettings?.let { open ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Все настройки уведомлений →",
                        style = AppTheme.Label,
                        color = AppTheme.Accent,
                        modifier = Modifier.clickable(role = Role.Button, onClick = open),
                    )
                }
            }
        }
    }
}

/** One rule as four chips: the three values the policy has, plus "как в общих" for removing it. */
@Composable
private fun NotifyRule(
    title: String,
    subtitle: String,
    value: String?,
    enabled: Boolean,
    onSelect: (String?) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, style = AppTheme.Body, color = AppTheme.Foreground)
        Text(text = subtitle, style = AppTheme.Footnote, color = AppTheme.Muted)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Absence is a value here, and the first one: "своего правила нет" is where most rows
            // stand, and a person undoing a mute needs it to be as reachable as setting one.
            NotifyChip("как в общих", value == null, enabled) { onSelect(null) }
            NotifyChip("присылать", value == "on", enabled) { onSelect("on") }
            NotifyChip("без звука", value == "silent", enabled) { onSelect("silent") }
            NotifyChip("не присылать", value == "off", enabled) { onSelect("off") }
        }
    }
}

@Composable
private fun NotifyChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = AppTheme.Label,
        color = if (selected) AppTheme.PrimaryForeground else AppTheme.Foreground,
        modifier = Modifier
            .clickable(role = Role.Button, enabled = enabled, onClick = onClick)
            .background(if (selected) AppTheme.Primary else AppTheme.Background, RoundedCornerShape(999.dp))
            .border(1.dp, AppTheme.Border, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
