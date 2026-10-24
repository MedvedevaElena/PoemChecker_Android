package com.example.poemchecker.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.poemchecker.domain.comparison.PoemComparator
import com.example.poemchecker.domain.model.*
import com.example.poemchecker.ui.exam.ExamScreen
import com.example.poemchecker.ui.recitation.RecitationPhase
import com.example.poemchecker.ui.recitation.RecitationUiState
import com.example.poemchecker.ui.training.TrainingScreen

private const val samplePoem = "Буря мглою небо кроет,\nВихри снежные крутя"

@Preview(showBackground = true, locale = "ru")
@Composable
private fun TrainingPreview() {
    MaterialTheme {
        TrainingScreen(
            state = RecitationUiState(
                session = SessionSnapshot(1, null, samplePoem, TrainingMode.FIRST_WORDS, SessionKind.TRAINING),
                comparison = PoemComparator.compare(samplePoem, "буря мглою"),
                phase = RecitationPhase.LISTENING
            ),
            onBack = {}, onToggleListening = {}
        )
    }
}

@Preview(showBackground = true, locale = "ru")
@Composable
private fun ExamPreview() {
    MaterialTheme {
        ExamScreen(
            state = RecitationUiState(
                session = SessionSnapshot(1, null, samplePoem, TrainingMode.NO_HINTS, SessionKind.EXAM)
            ),
            attempts = emptyList(), onBack = {}, onStart = {}
        )
    }
}
