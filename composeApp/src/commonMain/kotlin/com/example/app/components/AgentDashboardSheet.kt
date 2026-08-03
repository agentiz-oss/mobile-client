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
    webviewUrl: String?,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
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
            when {
                loading -> Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "Подключаем агента…", style = AppTheme.Body, color = AppTheme.Muted)
                }

                error != null -> Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(text = error, style = AppTheme.Body, color = AppTheme.Danger)
                    AppButton(text = "Повторить", onClick = onRetry, modifier = Modifier.padding(top = 16.dp))
                }

                webviewUrl != null -> EmbeddedDashboard(
                    // This one-use URL installs the HttpOnly dashboard session and redirects to
                    // the dedicated assistant page. It is intentionally not the generic dashboard.
                    url = webviewUrl,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
    }
}
