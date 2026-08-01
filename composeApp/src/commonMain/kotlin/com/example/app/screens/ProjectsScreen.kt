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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.example.app.components.AppButton
import com.example.app.data.AgentizApi
import com.example.app.data.ApiException
import com.example.app.data.ProjectDto
import com.example.app.data.Session
import com.example.app.theme.AppTheme

/**
 * The post-login screen: lists the projects the signed-in admin owns, fetched from the mobile API.
 * Handles the four states a network list has — loading, error (with retry), empty, and populated.
 */
@Composable
fun ProjectsScreen(session: Session, onOpenProject: (ProjectDto) -> Unit, onLogout: () -> Unit) {
    val api = remember(session.serverUrl) { AgentizApi(session.serverUrl) }
    DisposableEffect(api) { onDispose { api.close() } }

    var projects by remember { mutableStateOf<List<ProjectDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        loading = true
        error = null
        try {
            projects = api.projects(session.token)
        } catch (e: ApiException) {
            error = e.message
        } catch (e: Throwable) {
            error = "Ошибка сети: ${e.message ?: "неизвестная ошибка"}"
        } finally {
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.Background)
            .padding(24.dp),
    ) {
        Header(
            userLabel = session.user.fullName?.takeIf { it.isNotBlank() } ?: session.user.login,
            onLogout = onLogout,
        )
        Spacer(Modifier.height(24.dp))

        when {
            loading && projects == null -> CenterText("Загрузка проектов…")
            error != null -> ErrorState(message = error!!, onRetry = { reloadKey++ })
            projects.isNullOrEmpty() -> CenterText("У вас пока нет проектов.")
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(projects!!, key = { it.id }) { project ->
                    ProjectCard(project, onClick = { onOpenProject(project) })
                }
            }
        }
    }
}

@Composable
private fun Header(userLabel: String, onLogout: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.padding(end = 16.dp)) {
            Text(text = "Проекты", style = AppTheme.Title, color = AppTheme.Foreground)
            Spacer(Modifier.height(4.dp))
            Text(text = userLabel, style = AppTheme.Label, color = AppTheme.Muted)
        }
        AppButton(text = "Выйти", onClick = onLogout)
    }
}

@Composable
private fun ProjectCard(project: ProjectDto, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Clipped before the click so the press highlight follows the rounded card edge.
            .clip(RoundedCornerShape(AppTheme.Radius))
            .clickable(role = Role.Button, onClick = onClick)
            .border(1.dp, AppTheme.Border, RoundedCornerShape(AppTheme.Radius))
            .background(AppTheme.Surface, RoundedCornerShape(AppTheme.Radius))
            .padding(20.dp),
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
                modifier = Modifier.padding(end = 12.dp),
            )
            StatusBadge(active = project.isActive)
        }
        if (project.slug.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(text = project.slug, style = AppTheme.Label, color = AppTheme.Muted)
        }
        val description = project.description?.takeIf { it.isNotBlank() }
        if (description != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = description,
                style = AppTheme.Body,
                color = AppTheme.Muted,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StatusBadge(active: Boolean) {
    val color = if (active) AppTheme.Primary else AppTheme.Disabled
    Text(
        text = if (active) "активен" else "выключен",
        style = AppTheme.Label,
        color = AppTheme.PrimaryForeground,
        modifier = Modifier
            .background(color, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = message, style = AppTheme.Body, color = AppTheme.Danger)
        Spacer(Modifier.height(16.dp))
        AppButton(text = "Повторить", onClick = onRetry)
    }
}

@Composable
private fun CenterText(text: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = text, style = AppTheme.Body, color = AppTheme.Muted)
    }
}
