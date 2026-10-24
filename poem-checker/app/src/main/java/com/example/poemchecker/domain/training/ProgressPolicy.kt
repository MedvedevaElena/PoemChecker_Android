package com.example.poemchecker.domain.training

import com.example.poemchecker.domain.model.TrainingMode
import com.example.poemchecker.domain.model.TrainingProgress

data class ProgressUpdate(val progress: TrainingProgress, val nextMode: TrainingMode)

object ProgressPolicy {
    fun complete(mode: TrainingMode, score: Int, progress: TrainingProgress): ProgressUpdate {
        require(score in 0..100)
        val passed = score >= if (mode == TrainingMode.NO_HINTS) 90 else 85
        val updated = progress.copy(
            firstWordsPassed = progress.firstWordsPassed || (mode == TrainingMode.FIRST_WORDS && passed),
            lastWordsPassed = progress.lastWordsPassed || (mode == TrainingMode.LAST_WORDS && passed),
            finalPassed = progress.finalPassed || (mode == TrainingMode.NO_HINTS && passed),
            history = (progress.history + score).takeLast(20),
            bestScore = maxOf(progress.bestScore, score)
        )
        val nextMode = when {
            mode == TrainingMode.FIRST_WORDS && passed -> TrainingMode.LAST_WORDS
            mode == TrainingMode.LAST_WORDS && passed -> TrainingMode.NO_HINTS
            else -> mode
        }
        return ProgressUpdate(updated, nextMode)
    }
}
