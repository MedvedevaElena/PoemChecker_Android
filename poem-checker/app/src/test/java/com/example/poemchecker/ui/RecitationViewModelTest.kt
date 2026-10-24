package com.example.poemchecker.ui

import com.example.poemchecker.domain.model.*
import com.example.poemchecker.domain.training.FinishRecitation
import com.example.poemchecker.fixtures.*
import com.example.poemchecker.ui.recitation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecitationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakePoemRepository
    private lateinit var speech: FakeSpeechClient
    private lateinit var viewModel: RecitationViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakePoemRepository()
        speech = FakeSpeechClient()
        viewModel = RecitationViewModel(speech, FinishRecitation(repository) { 123L }, dispatcher)
    }
    @After fun tearDown() { viewModel.close(); Dispatchers.resetMain() }

    private fun open(kind: SessionKind = SessionKind.TRAINING, text: String = "раз два", saved: Boolean = true) {
        assertTrue(viewModel.open(if (saved) 7 else null, text, TrainingMode.FIRST_WORDS, kind, TrainingProgress()))
        viewModel.startListening()
    }

    @Test fun invalidReferenceCannotOpenExam() = runTest(dispatcher) {
        assertFalse(viewModel.open(null, "...\n", TrainingMode.NO_HINTS, SessionKind.EXAM, TrainingProgress()))
    }

    @Test fun savesOriginalModeAndFinishesOnlyOnce() = runTest(dispatcher) {
        open()
        speech.emit("раз два")
        advanceUntilIdle()
        viewModel.finish()
        advanceUntilIdle()
        assertEquals(1, repository.attempts.value.size)
        assertEquals("FIRST_WORDS", repository.attempts.value.single().mode)
        assertEquals(123L, repository.attempts.value.single().createdAt)
        assertEquals(TrainingMode.LAST_WORDS, viewModel.state.value.result!!.update.nextMode)
        assertTrue(speech.cancelled)
    }

    @Test fun noHintsExamNeedsManualFinishAndGetsItsOwnResult() = runTest(dispatcher) {
        open()
        speech.emit("раз два")
        advanceUntilIdle()
        open(SessionKind.EXAM)
        assertNull(viewModel.state.value.result)
        speech.emit("раз два")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isListening)
        assertNull(viewModel.state.value.result)
        viewModel.finish()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.trainingProgress.finalPassed)
        assertEquals("NO_HINTS", repository.attempts.value.last().mode)
    }

    @Test fun longPoemCannotAutoStopAt180Words() = runTest(dispatcher) {
        val words = (1..250).map { "слово$it" }
        open(text = words.joinToString(" "))
        speech.emit(words.take(200).joinToString(" "))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isListening)
        assertTrue(repository.attempts.value.isEmpty())
        speech.emit(words.joinToString(" "))
        advanceUntilIdle()
        assertEquals(RecitationPhase.FINISHED, viewModel.state.value.phase)
    }

    @Test fun cancelledSessionIgnoresLateRecognitionCallbacks() = runTest(dispatcher) {
        open()
        val oldText = speech.onText
        val oldError = speech.onError
        viewModel.close()
        open(text = "три четыре")
        oldText("раз два")
        oldError("late provider failure")
        advanceUntilIdle()
        assertEquals("", viewModel.state.value.recognizedText)
        assertTrue(viewModel.state.value.isListening)
        assertTrue(repository.attempts.value.isEmpty())
    }

    @Test fun leavingOrBackgroundingCancelsWithoutRecordingAttempt() = runTest(dispatcher) {
        open(SessionKind.EXAM)
        speech.emit("раз")
        viewModel.cancelListening()
        advanceUntilIdle()
        assertTrue(speech.cancelled)
        assertFalse(viewModel.state.value.isListening)
        assertTrue(repository.attempts.value.isEmpty())
        assertTrue(viewModel.close())
        assertNull(viewModel.state.value.session)
    }

    @Test fun unsavedPoemUpdatesInMemoryWithoutStorageWrite() = runTest(dispatcher) {
        open(saved = false)
        speech.emit("раз два")
        advanceUntilIdle()
        assertEquals(100, viewModel.state.value.trainingProgress.bestScore)
        assertTrue(repository.attempts.value.isEmpty())
    }

    @Test fun failedSaveCanRetryWithoutLosingAttemptMode() = runTest(dispatcher) {
        repository.failNextAttempt = true
        open()
        speech.emit("раз два")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.canRetrySave)
        assertTrue(repository.attempts.value.isEmpty())
        viewModel.finish()
        advanceUntilIdle()
        assertEquals("FIRST_WORDS", repository.attempts.value.single().mode)
        assertEquals(1, viewModel.state.value.trainingProgress.history.size)
    }

    @Test fun newerPartialResultWinsOverQueuedComparison() = runTest(dispatcher) {
        open(SessionKind.EXAM)
        speech.emit("раз")
        speech.emit("раз два")
        advanceUntilIdle()
        assertEquals(100, viewModel.state.value.comparison!!.scorePercent)
        assertEquals("раз два", viewModel.state.value.recognizedText)
    }
}
