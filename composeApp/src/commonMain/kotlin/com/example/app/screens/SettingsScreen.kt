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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.app.components.BellIcon
import com.example.app.components.ForwardIcon
import com.example.app.components.MenuEntry
import com.example.app.components.GlobeIcon
import com.example.app.data.AgentizApi
import com.example.app.data.Session
import com.example.app.i18n.AppLocale
import com.example.app.i18n.Lang
import com.example.app.i18n.strings
import com.example.app.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * Settings — a hub, not a settings page.
 *
 * Notifications used to live here in full, and a screen that is one long matrix has nowhere to put
 * the second setting when it arrives. So this is a list of rows that lead somewhere: each row says
 * what it covers, and the настройки themselves live on their own screen.
 */
@Composable
fun SettingsScreen(
    session: Session,
    menu: List<MenuEntry>,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
    onLocaleSaved: (String) -> Unit,
) {
    AppScaffold(
        title = strings.settingsTitle,
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
                title = strings.settingsNotifications,
                description = strings.settingsNotificationsHint,
                icon = { tint -> BellIcon(tint = tint, size = 18.dp) },
                onClick = onOpenNotifications,
            )
            LanguageCard(session = session, onSaved = onLocaleSaved)
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

/**
 * The language of the app, as a row of choices rather than a page of its own.
 *
 * Three options fit on one line, and a hub row leading to a screen holding three radio buttons is a
 * tap spent on nothing. Each option is written *in its own language*, because a picker labelled in
 * the language the reader is trying to leave is the one control they cannot use.
 *
 * The switch is local and immediate; writing it to `UserAP.locale` is what makes it the setting
 * rather than this device's opinion, and that write is allowed to fail without taking the switch
 * back — the reader asked for this language, and the profile catching up is the server's problem,
 * not a reason to keep showing them a language they did not choose.
 */
@Composable
private fun LanguageCard(session: Session, onSaved: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val selected = AppLocale.lang

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AppTheme.Border, RoundedCornerShape(AppTheme.Radius))
            .background(AppTheme.Surface, RoundedCornerShape(AppTheme.Radius))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(AppTheme.Background, CircleShape)
                    .border(1.dp, AppTheme.Border, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                GlobeIcon(tint = AppTheme.Muted, size = 18.dp)
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = strings.settingsLanguage, style = AppTheme.Subtitle, color = AppTheme.Foreground)
                Text(
                    text = strings.settingsLanguageHint,
                    style = AppTheme.Label,
                    color = AppTheme.Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (lang in Lang.entries) {
                LanguagePill(
                    label = lang.nativeName,
                    selected = lang == selected,
                    enabled = !saving,
                    onClick = {
                        // Applied first: the screen is already in the new language by the time the
                        // request leaves, and a slow or absent network cannot make the tap look lost.
                        AppLocale.choose(lang)
                        failed = false
                        saving = true
                        scope.launch {
                            val api = AgentizApi(session.serverUrl)
                            try {
                                runCatching { api.setLocale(session.token, lang.tag) }
                                    .onSuccess { onSaved(lang.tag) }
                                    .onFailure { failed = true }
                            } finally {
                                api.close()
                                saving = false
                            }
                        }
                    },
                )
            }
        }
        if (saving || failed) {
            Spacer(Modifier.size(8.dp))
            Text(
                text = if (saving) strings.languageSaving else strings.languageSaveFailed,
                style = AppTheme.Label,
                color = if (failed) AppTheme.Danger else AppTheme.Muted,
            )
        }
    }
}

/** One language of the picker. Same pill shape the notification rules and the log filter wear. */
@Composable
private fun LanguagePill(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = AppTheme.Label,
        color = when {
            selected -> AppTheme.PrimaryForeground
            enabled -> AppTheme.Foreground
            else -> AppTheme.Disabled
        },
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) AppTheme.Primary else AppTheme.Background,
                RoundedCornerShape(999.dp),
            )
            .border(
                1.dp,
                if (selected) AppTheme.Primary else AppTheme.Border,
                RoundedCornerShape(999.dp),
            )
            .clickable(role = Role.Button, enabled = enabled && !selected, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
