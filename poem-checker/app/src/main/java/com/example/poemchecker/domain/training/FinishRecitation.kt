package com.example.poemchecker.domain.training

import com.example.poemchecker.domain.comparison.ComparisonResult
import com.example.poemchecker.domain.model.Attempt
import com.example.poemchecker.domain.model.SessionSnapshot
import com.example.poemchecker.domain.model.TrainingProgress
import com.example.poemchecker.domain.repository.PoemRepository

class FinishRecitation(
    private val repository: PoemRepository,
    private val now: () -> Long = System::currentTimeMillis
) {
    suspend operator fun invoke(
        session: SessionSnapshot,
        recognizedText: String,
        comparison: ComparisonResult,
        progress: TrainingProgress
    ): ProgressUpdate {
        require(comparison.expectedCount > 0) { "A recitation needs a nonempty reference poem" }
        val update = ProgressPolicy.complete(session.mode, comparison.scorePercent, progress)
        session.poemId?.let { poemId ->
            repository.recordAttempt(
                Attempt(
                    poemId = poemId,
                    mode = session.mode.name,
                    scorePercent = comparison.scorePercent,
                    createdAt = now(),
                    recognizedPreview = recognizedText.take(120)
                ),
                update.progress
            )
        }
        return update
    }
}
