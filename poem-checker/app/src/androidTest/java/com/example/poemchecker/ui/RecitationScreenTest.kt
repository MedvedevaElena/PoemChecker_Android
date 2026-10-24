package com.example.poemchecker.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.poemchecker.domain.model.*
import com.example.poemchecker.domain.comparison.PoemComparator
import com.example.poemchecker.ui.recitation.*
import com.example.poemchecker.ui.training.TrainingScreen
import com.example.poemchecker.ui.exam.ExamScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RecitationScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun trainingShowsHintButHidesUnspokenNonHintWords() {
        compose.setContent {
            MaterialTheme {
                TrainingScreen(
                    state = RecitationUiState(
                        session = SessionSnapshot(1, null, "первое второе", TrainingMode.FIRST_WORDS, SessionKind.TRAINING),
                        comparison = PoemComparator.compare("первое второе", ""),
                        phase = RecitationPhase.LISTENING
                    ),
                    onBack = {}, onToggleListening = {}
                )
            }
        }
        compose.onNodeWithText("первое").assertIsDisplayed()
        compose.onNodeWithText("второе").assertDoesNotExist()
    }

    @Test fun examDoesNotLabelPartialComparisonAsFinishedResult() {
        var backedOut = false
        compose.setContent {
            MaterialTheme {
                ExamScreen(
                    state = RecitationUiState(
                        session = SessionSnapshot(1, null, "секретный текст", TrainingMode.NO_HINTS, SessionKind.EXAM),
                        comparison = PoemComparator.compare("секретный текст", "секретный текст"),
                        phase = RecitationPhase.LISTENING
                    ),
                    attempts = emptyList(), onBack = { backedOut = true }, onStart = {}
                )
            }
        }
        compose.onNodeWithText("Последний результат экзамена").assertDoesNotExist()
        compose.onNodeWithText("секретный текст").assertDoesNotExist()
        compose.onNodeWithContentDescription("Назад").performClick()
        assertTrue(backedOut)
    }
}
