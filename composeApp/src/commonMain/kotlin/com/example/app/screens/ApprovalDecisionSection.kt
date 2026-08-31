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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.example.app.components.AppButton
import com.example.app.components.AppTextField
import com.example.app.data.ApprovalDto
import com.example.app.theme.AppTheme

/**
 * The decision block of a workflow's human gate: accept the work, or send it back with a reason.
 *
 * Deliberately not the proposal block next door, because the question is a different one. A
 * proposal asks «коммитить ли этот диф» and holds a worker's directory while nobody answers; this
 * asks «сделано ли то, что просили» and holds only the flow — it can wait for days, and the two
 * outcomes are two ports of a graph rather than two git operations.
 *
 * Three rules of the surface, all of them the server's and none invented here:
 *
 * * **a rejection needs a text.** The endpoint refuses one without it (HTTP 400), because that
 *   text is handed to the agent verbatim as its next instruction — "отклонено" with no reason
 *   sends it to redo the task from scratch. So the button stays disabled until something is typed,
 *   rather than letting the person discover the rule from an error;
 * * **the facts are shown, the prose is not.** Verdict, branch, commit — the same facts the inbox
 *   row carries. The agent's summary is on the run screen, one tap away through «Посмотреть
 *   изменения», and is not repeated here as several screens of reasoning;
 * * **the links are what the graph attached** — a preview stand, a board, an external report. They
 *   open in the system browser: whatever is behind them is not part of this app.
 */
@Composable
fun ApprovalDecisionSection(
    approval: ApprovalDto,
    busy: Boolean,
    /** Which half to open with — the card that opened this block already asked the question once. */
    initialMode: String? = null,
    onApprove: (comment: String?) -> Unit,
    onReject: (comment: String) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    if (approval.status != "pending") {
        Text(
            text = when (approval.status) {
                "approved" -> "Уже принято."
                "rejected" -> "Уже отклонено."
                "cancelled" -> "Заявка снята: воркфлоу отменили."
                else -> "Решение уже принято."
            },
            style = AppTheme.Label,
            color = AppTheme.Muted,
        )
        return
    }

    var mode by remember(approval.id, initialMode) {
        mutableStateOf(initialMode?.takeIf { it == "approve" || it == "reject" })
    }
    var comment by remember(approval.id) { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AppTheme.Border, RoundedCornerShape(AppTheme.Radius))
            .background(AppTheme.Surface, RoundedCornerShape(AppTheme.Radius))
            .padding(20.dp),
    ) {
        SectionTitle(approval.title.ifBlank { "Примите работу" })

        val facts = listOfNotNull(
            approval.runVerdict?.let { "вердикт агента: ${if (it == "pass") "ок" else "не ок"}" },
            approval.runBranch?.takeIf { it.isNotBlank() }?.let { "ветка $it" },
            approval.runCommitSha?.takeIf { it.isNotBlank() }?.let { "коммит ${it.take(12)}" },
        ).joinToString(" · ")
        if (facts.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(text = facts, style = AppTheme.Label, color = AppTheme.Muted)
        }
        approval.message?.takeIf { it.isNotBlank() }?.let { message ->
            Spacer(Modifier.height(8.dp))
            Text(text = message, style = AppTheme.Body, color = AppTheme.Foreground)
        }
        // Only for a `fail`: on a `pass` this is the agent explaining why it is happy, which is
        // prose, and prose does not belong on the surface where a decision is made.
        approval.runVerdictReason?.takeIf { it.isNotBlank() && approval.runVerdict == "fail" }?.let { reason ->
            Spacer(Modifier.height(6.dp))
            Text(text = reason, style = AppTheme.Label, color = AppTheme.Warning)
        }
        if (approval.links.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                approval.links.forEach { link ->
                    Text(
                        text = link.label.ifBlank { link.url },
                        style = AppTheme.Label,
                        color = AppTheme.Accent,
                        modifier = Modifier.clickable(role = Role.Button) {
                            runCatching { uriHandler.openUri(link.url) }
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        when (mode) {
            "approve" -> {
                Text(
                    text = "Принять работу? Воркфлоу пойдёт дальше по ветке «принято». Комментарий"
                        + " не обязателен — он останется в истории решения.",
                    style = AppTheme.Body,
                    color = AppTheme.Foreground,
                )
                Spacer(Modifier.height(10.dp))
                AppTextField(
                    label = "Комментарий (не обязательно)",
                    value = comment,
                    onValueChange = { comment = it },
                    minLines = 2,
                    enabled = !busy,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppButton(
                        text = if (busy) "Отправляется…" else "Да, принять",
                        onClick = { onApprove(comment.trim().takeIf { it.isNotBlank() }) },
                        enabled = !busy,
                    )
                    AppButton(text = "Назад", onClick = { mode = null }, enabled = !busy)
                }
            }

            "reject" -> {
                Text(
                    text = "Что не так? Этот текст уедет разработчику как задание на доработку, и"
                        + " по задаче начнётся новый круг.",
                    style = AppTheme.Body,
                    color = AppTheme.Foreground,
                )
                Spacer(Modifier.height(10.dp))
                AppTextField(
                    label = "Замечания",
                    value = comment,
                    onValueChange = { comment = it },
                    placeholder = "Подвал съезжает на узком экране",
                    minLines = 3,
                    enabled = !busy,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppButton(
                        text = if (busy) "Отправляется…" else "Отклонить и вернуть",
                        // The server refuses an empty reason; refusing it here means the person
                        // never meets that error.
                        onClick = { onReject(comment.trim()) },
                        enabled = !busy && comment.isNotBlank(),
                    )
                    AppButton(text = "Назад", onClick = { mode = null }, enabled = !busy)
                }
            }

            else -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppButton(text = "Принять…", onClick = { mode = "approve" }, enabled = !busy)
                AppButton(text = "Отклонить…", onClick = { mode = "reject" }, enabled = !busy)
            }
        }
    }
}
