package com.example.poemchecker.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.poemchecker.MainActivity
import org.junit.Rule
import org.junit.Test

class AppNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun systemBackReturnsFromExamToTheSameDraft() {
        compose.onNode(hasSetTextAction()).performTextInput("текст для экзамена")
        compose.onNodeWithText("Открыть экран экзамена").performScrollTo().performClick()
        compose.onNodeWithText("Экзамен без подсказок").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNode(hasSetTextAction()).assertTextContains("текст для экзамена")
        compose.onNodeWithText("Экзамен без подсказок").assertDoesNotExist()
    }
}
