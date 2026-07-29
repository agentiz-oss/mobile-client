package com.example.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

class LoginScreenTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsLoginFormWhenSignedOut() = runComposeUiTest {
        setContent { App() }
        waitForIdle()

        // With no session the app opens on the login screen.
        onNodeWithText("Agentiz").assertIsDisplayed()
        onNodeWithText("Логин").assertIsDisplayed()
        onNodeWithText("Пароль").assertIsDisplayed()
        onNodeWithText("Войти").assertIsDisplayed()
    }
}
