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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.example.app.components.AppScaffold
import com.example.app.components.BellIcon
import com.example.app.components.ForwardIcon
import com.example.app.components.MenuEntry
import com.example.app.theme.AppTheme

/**
 * Settings — a hub, not a settings page.
 *
 * Notifications used to live here in full, and a screen that is one long matrix has nowhere to put
 * the second setting when it arrives. So this is a list of rows that lead somewhere: each row says
 * what it covers, and the настройки themselves live on their own screen.
 */
@Composable
fun SettingsScreen(
    menu: List<MenuEntry>,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
) {
    AppScaffold(
        title = "Настройки",
        menu = menu,
        onOpenSettings = onOpenSettings,
        onOpenProfile = onOpenProfile,
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsRow(
                title = "Уведомления",
                description = "Пуш и колокольчик: общие правила и отдельно по каждому проекту",
                icon = { tint -> BellIcon(tint = tint, size = 18.dp) },
                onClick = onOpenNotifications,
            )
        }
    }
}

/** One entry of the hub: icon, what it covers, and a chevron saying it opens something. */
@Composable
private fun SettingsRow(
    title: String,
    description: String,
    icon: @Composable (Color) -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .border(1.dp, AppTheme.Border, RoundedCornerShape(AppTheme.Radius))
            .background(AppTheme.Surface, RoundedCornerShape(AppTheme.Radius))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(AppTheme.Background, CircleShape)
                .border(1.dp, AppTheme.Border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            icon(AppTheme.Muted)
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = AppTheme.Subtitle, color = AppTheme.Foreground)
            Text(
                text = description,
                style = AppTheme.Label,
                color = AppTheme.Muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ForwardIcon(AppTheme.Muted, size = 16.dp)
    }
}
