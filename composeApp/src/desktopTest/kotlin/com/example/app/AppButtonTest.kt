package com.example.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

class AppButtonTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun clickingButtonUpdatesTitleAndLabel() = runComposeUiTest {
        setContent { App() }
        waitForIdle()

        onNodeWithText("Hello Compose").assertIsDisplayed()
        onNodeWithText("Click me").assertIsDisplayed()

        onNodeWithText("Click me").performClick()
        waitForIdle()

        onNodeWithText("Hello Compose!").assertIsDisplayed()
        onNodeWithText("Clicked").assertIsDisplayed()
    }
}
