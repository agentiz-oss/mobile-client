package com.example.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.example.app.data.RunOptionsDto
import com.example.app.theme.AppTheme

/**
 * What the next launch will use, and the three things a person may change about it.
 *
 * Null everywhere means "как настроен пайплайн" — the launch then sends nothing and the run is
 * byte-identical to what the button did before this block existed.
 */
data class RunChoice(
    val workerId: String? = null,
    val executorKey: String? = null,
    val model: String? = null,
    val reasoningLevel: String? = null,
)

/**
 * Collapsed, this is one muted line naming the runner, the model and the thinking level the run
 * will actually get — the question "чем оно сейчас поедет" used to have no answer on the phone at
 * all. Tapping it opens the pickers; nothing is chosen until somebody taps a pill, so the button
 * below keeps working exactly as before for anyone who never opens it.
 */
@Composable
fun RunOptionsSection(
    options: RunOptionsDto,
    choice: RunChoice,
    expanded: Boolean,
    enabled: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onChoice: (RunChoice) -> Unit,
) {
    val executor = options.executors.firstOrNull {
        it.workerId == choice.workerId && it.executorKey == choice.executorKey
    }
    // A chosen runner decides which models are on offer; with none chosen that is the pipeline's
    // own harness, which is also the one the defaults were resolved from.
    val harnessKey = executor?.harnessKey ?: options.defaults.harnessKey
    val harness = options.harnesses.firstOrNull { it.key == harnessKey }
    val harnessTitle = executor?.title ?: harness?.title ?: options.defaults.harnessTitle
    val model = choice.model ?: options.defaults.model
    val levelTitle = choice.reasoningLevel
        ?.let { value -> options.reasoningLevels.firstOrNull { it.value == value }?.title ?: value }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AppTheme.Border, RoundedCornerShape(AppTheme.Radius))
            .background(AppTheme.Surface, RoundedCornerShape(AppTheme.Radius))
            .clickable(role = Role.Button) { onExpandedChange(!expanded) }
            .padding(16.dp),
    ) {
        val summary = listOfNotNull(
            harnessTitle ?: "обвязка по пайплайну",
            model ?: "модель по умолчанию",
            levelTitle?.let { "уровень: ${it.lowercase()}" } ?: "уровень: как у CLI",
        ).joinToString(" · ")
        SectionTitle(if (expanded) "Чем запускать" else "Запустится: $summary")

        if (!expanded) {
            Spacer(Modifier.height(6.dp))
            Text(text = "Нажмите, чтобы выбрать", style = AppTheme.Label, color = AppTheme.Muted)
            return@Column
        }

        if (options.executors.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            PickerRow(
                title = "Обвязка",
                options = listOf(null to defaultLabel(options.defaults.harnessTitle))
                    + options.executors.map { "${it.workerId}:${it.executorKey}" to "${it.title} · ${it.workerName}" },
                selected = choice.workerId?.let { "$it:${choice.executorKey}" },
                enabled = enabled,
                onSelect = { value ->
                    val picked = options.executors.firstOrNull { "${it.workerId}:${it.executorKey}" == value }
                    // Switching runners drops a model picked for the previous one: model ids are
                    // harness vocabulary, and "gpt-5.5" sent to Claude is a failed run.
                    onChoice(
                        choice.copy(
                            workerId = picked?.workerId,
                            executorKey = picked?.executorKey,
                            model = if (picked?.harnessKey == harnessKey) choice.model else null,
                        ),
                    )
                },
            )
        }

        val models = harness?.models.orEmpty()
        if (models.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            PickerRow(
                title = "Модель",
                options = listOf(null to defaultLabel(options.defaults.model)) + models.map { it.id to it.title },
                selected = choice.model,
                enabled = enabled,
                onSelect = { onChoice(choice.copy(model = it)) },
            )
        }

        val levels = options.reasoningLevels.filter { level ->
            harness == null || harness.reasoningLevels.isEmpty() || level.value in harness.reasoningLevels
        }
        if (levels.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            PickerRow(
                title = "Уровень рассуждений",
                options = listOf(null to "По умолчанию") + levels.map { it.value to it.title },
                selected = choice.reasoningLevel,
                enabled = enabled,
                onSelect = { onChoice(choice.copy(reasoningLevel = it)) },
            )
        }

        if (options.stages.size > 1) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Выбор применяется ко всем этапам запуска (${options.stages.size})",
                style = AppTheme.Label,
                color = AppTheme.Muted,
            )
        }
    }
}

/** The "не выбрано" pill names what the pipeline itself would use, so the row has no blank end. */
private fun defaultLabel(value: String?): String =
    if (value.isNullOrBlank()) "Как в пайплайне" else "Как в пайплайне ($value)"

@Composable
private fun PickerRow(
    title: String,
    options: List<Pair<String?, String>>,
    selected: String?,
    enabled: Boolean,
    onSelect: (String?) -> Unit,
) {
    SectionTitle(title)
    Spacer(Modifier.height(8.dp))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Text(
                text = label,
                style = AppTheme.Label,
                color = if (active) AppTheme.PrimaryForeground else AppTheme.Foreground,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (active) AppTheme.Primary else AppTheme.Background, RoundedCornerShape(999.dp))
                    .border(1.dp, if (active) AppTheme.Primary else AppTheme.Border, RoundedCornerShape(999.dp))
                    .clickable(enabled = enabled, role = Role.Button) { onSelect(value) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}
