package com.example.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.example.app.data.Session
import com.example.app.data.UserDto
import com.example.app.data.saveSession
import com.example.app.data.useInMemorySessionStorageForTesting
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Settings is a hub: notifications used to be its whole body and now live one screen deeper, so
 * the path into them — and back out — is what this covers. The notifications page needs a server
 * to render anything below its title, so the row's description is the landmark for the hub and the
 * page title is the landmark for the page.
 */
class SettingsHubTest {

    @BeforeTest
    fun isolateSessionStorage() = useInMemorySessionStorageForTesting()

    private fun signIn() = saveSession(
        Session(
            serverUrl = "https://example.invalid",
            token = "token-123",
            user = UserDto(login = "admin", fullName = "Тест Тестов"),
        ),
    )

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theSettingsRowOpensNotificationsAndBackReturnsToTheHub() = runComposeUiTest {
        signIn()
        setContent { App() }
        waitForIdle()

        onNodeWithContentDescription("Открыть меню").performClick()
        waitForIdle()
        onNodeWithContentDescription("Настройки").performClick()
        waitForIdle()

        // The row describes what it leads to; the word "Уведомления" alone is also the next page's
        // title, so the description is what tells the two apart.
        onNodeWithText("Пуш и колокольчик: общие правила и отдельно по каждому проекту").assertIsDisplayed()

        onNodeWithText("Уведомления").performClick()
        waitForIdle()

        onNodeWithText("Уведомления").assertIsDisplayed()
        onNodeWithText("Пуш и колокольчик: общие правила и отдельно по каждому проекту").assertDoesNotExist()

        onNodeWithContentDescription("Назад").performClick()
        waitForIdle()

        // Back lands on the hub it was opened from, not on the screen the hub itself came from.
        onNodeWithText("Пуш и колокольчик: общие правила и отдельно по каждому проекту").assertIsDisplayed()
    }
}
