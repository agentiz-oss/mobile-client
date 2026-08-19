package com.example.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.example.app.components.AppButton
import com.example.app.components.AppTextField
import com.example.app.data.ProposalDto
import com.example.app.theme.AppTheme

/**
 * The decision block of a workspace proposal: approve (commit + push by the pinned worker) or
 * reject (reset the workspace). Shown on the run's page when its proposal still needs a person —
 * `waiting_review` and `push_failed` offer both, `reset_failed` only the retryable reject.
 *
 * Both actions confirm in place (no native dialogs — the worker panel made that a convention):
 * approve expands into the editable commit message / target branch first, reject into a one-line
 * "точно?". The revision travels with either decision, so a phone looking at yesterday's revision
 * gets the server's 409 instead of pushing something unseen.
 */
@Composable
fun ProposalReviewSection(
    proposal: ProposalDto,
    busy: Boolean,
    onApprove: (revision: Int, targetBranch: String?, commitMessage: String?) -> Unit,
    onReject: (revision: Int) -> Unit,
) {
    val canApprove = proposal.approvable && proposal.status in listOf("waiting_review", "push_failed")
    val canReject = proposal.status in listOf("waiting_review", "push_failed", "reset_failed")
    if (!canApprove && !canReject) return

    var mode by remember(proposal.id, proposal.revision, proposal.status) { mutableStateOf<String?>(null) }
    var commitMessage by remember(proposal.id, proposal.revision) { mutableStateOf(proposal.commitMessage ?: "") }
    var targetBranch by remember(proposal.id, proposal.revision) { mutableStateOf(proposal.targetBranch ?: "") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AppTheme.Border, RoundedCornerShape(AppTheme.Radius))
            .background(AppTheme.Surface, RoundedCornerShape(AppTheme.Radius))
            .padding(20.dp),
    ) {
        SectionTitle(
            when (proposal.status) {
                "push_failed" -> "Push не прошёл — решите, что дальше"
                "reset_failed" -> "Сброс не прошёл — можно повторить"
                else -> "Изменения ждут ревью"
            },
        )
        Spacer(Modifier.height(8.dp))
        val summaryLine = listOfNotNull(
            "Ревизия ${proposal.revision}",
            proposal.diff?.let { diff ->
                val stats = diff.stats
                if (stats != null) "${stats.files} файл(ов), +${stats.insertions}/−${stats.deletions}" else "${diff.operations} операций"
            },
            proposal.targetBranch?.takeIf { it.isNotBlank() }?.let { "ветка $it" },
        ).joinToString(" · ")
        Text(text = summaryLine, style = AppTheme.Label, color = AppTheme.Muted)
        proposal.lastError?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(6.dp))
            Text(text = it, style = AppTheme.Label, color = AppTheme.Danger)
        }
        Spacer(Modifier.height(12.dp))

        when (mode) {
            "approve" -> {
                AppTextField(
                    label = "Сообщение коммита",
                    value = commitMessage,
                    onValueChange = { commitMessage = it },
                    minLines = 2,
                )
                if (proposal.targetMode == "new") {
                    Spacer(Modifier.height(8.dp))
                    AppTextField(
                        label = "Ветка",
                        value = targetBranch,
                        onValueChange = { targetBranch = it },
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppButton(
                        text = if (busy) "Отправляется…" else "Закоммитить и запушить",
                        onClick = {
                            onApprove(
                                proposal.revision,
                                targetBranch.trim().takeIf { it.isNotBlank() },
                                commitMessage.trim().takeIf { it.isNotBlank() },
                            )
                        },
                        enabled = !busy,
                    )
                    AppButton(text = "Назад", onClick = { mode = null }, enabled = !busy)
                }
            }
            "reject" -> {
                Text(
                    text = "Отклонить ревизию и сбросить воркспейс? Наработки этой ревизии будут удалены с воркера.",
                    style = AppTheme.Body,
                    color = AppTheme.Foreground,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppButton(
                        text = if (busy) "Отправляется…" else "Да, отклонить",
                        onClick = { onReject(proposal.revision) },
                        enabled = !busy,
                    )
                    AppButton(text = "Назад", onClick = { mode = null }, enabled = !busy)
                }
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (canApprove) {
                    AppButton(text = "Одобрить…", onClick = { mode = "approve" }, enabled = !busy)
                }
                if (canReject) {
                    AppButton(
                        text = if (proposal.status == "reset_failed") "Повторить сброс…" else "Отклонить…",
                        onClick = { mode = "reject" },
                        enabled = !busy,
                    )
                }
                Spacer(Modifier.width(0.dp))
            }
        }
    }
}
