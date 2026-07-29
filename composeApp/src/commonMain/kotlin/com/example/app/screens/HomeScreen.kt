package com.example.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.example.app.components.AppButton
import com.example.app.theme.AppTheme

/**
 * The single screen of the app: a centered title with one large primary button.
 * Clicking the button updates both the title and the button label.
 */
@Composable
fun HomeScreen() {
    var clicked by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.Background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (clicked) "Hello Compose!" else "Hello Compose",
            style = AppTheme.Title,
            color = AppTheme.Foreground,
        )
        Spacer(Modifier.height(32.dp))
        AppButton(
            text = if (clicked) "Clicked" else "Click me",
            onClick = { clicked = true },
        )
    }
}
