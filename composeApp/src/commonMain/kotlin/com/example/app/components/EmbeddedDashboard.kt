package com.example.app.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Platform web surface used to show the Agentiz dashboard inside the bottom sheet. */
@Composable
expect fun EmbeddedDashboard(url: String, modifier: Modifier = Modifier)
