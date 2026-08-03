package com.example.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.example.app.theme.AppTheme

/**
 * A modal sheet which hosts the dashboard itself, where the full agent chat lives. This keeps the
 * chat in one place while preserving the user's context in the mobile client.
 */
@Composable
fun AgentDashboardSheet(
    dashboardUrl: String,
    onDismiss: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppTheme.Foreground.copy(alpha = 0.38f))
                .clickable(role = Role.Button, onClick = onDismiss)
                .semantics { contentDescription = "Закрыть окно агента" },
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(AppTheme.Background)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AppTheme.Border),
            )
            Text(text = "Диалог с агентом", style = AppTheme.Subtitle, color = AppTheme.Foreground)
            Text(text = "Админка Agentiz", style = AppTheme.Label, color = AppTheme.Muted)
            EmbeddedDashboard(
                url = dashboardUrl,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}
