package com.example.poemchecker.domain.training

import com.example.poemchecker.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class ProgressPolicyTest {
    @Test fun firstWordsThresholdIs85() {
        assertFalse(ProgressPolicy.complete(TrainingMode.FIRST_WORDS, 84, TrainingProgress()).progress.firstWordsPassed)
        val passed = ProgressPolicy.complete(TrainingMode.FIRST_WORDS, 85, TrainingProgress())
        assertTrue(passed.progress.firstWordsPassed)
        assertEquals(TrainingMode.LAST_WORDS, passed.nextMode)
    }

    @Test fun lastWordsThresholdIs85() {
        assertFalse(ProgressPolicy.complete(TrainingMode.LAST_WORDS, 84, TrainingProgress()).progress.lastWordsPassed)
        assertEquals(TrainingMode.NO_HINTS, ProgressPolicy.complete(TrainingMode.LAST_WORDS, 85, TrainingProgress()).nextMode)
    }

    @Test fun noHintsThresholdIs90() {
        assertFalse(ProgressPolicy.complete(TrainingMode.NO_HINTS, 89, TrainingProgress()).progress.finalPassed)
        assertTrue(ProgressPolicy.complete(TrainingMode.NO_HINTS, 90, TrainingProgress()).progress.finalPassed)
    }

    @Test fun failedRepeatKeepsPassButDoesNotAdvance() {
        val update = ProgressPolicy.complete(TrainingMode.FIRST_WORDS, 0, TrainingProgress(firstWordsPassed = true))
        assertTrue(update.progress.firstWordsPassed)
        assertEquals(TrainingMode.FIRST_WORDS, update.nextMode)
    }

    @Test fun combinedHintsOnlyUpdateHistoryAndBest() {
        val update = ProgressPolicy.complete(TrainingMode.FIRST_AND_LAST_WORDS, 100, TrainingProgress())
        assertFalse(update.progress.firstWordsPassed)
        assertFalse(update.progress.lastWordsPassed)
        assertFalse(update.progress.finalPassed)
        assertEquals(listOf(100), update.progress.history)
        assertEquals(100, update.progress.bestScore)
        assertEquals(TrainingMode.FIRST_AND_LAST_WORDS, update.nextMode)
    }

    @Test fun historyKeeps20AndBestDoesNotDecrease() {
        val previous = TrainingProgress(history = List(20) { 100 }, bestScore = 100)
        val updated = ProgressPolicy.complete(TrainingMode.NO_HINTS, 50, previous).progress
        assertEquals(20, updated.history.size)
        assertEquals(50, updated.history.last())
        assertEquals(100, updated.bestScore)
    }
}
