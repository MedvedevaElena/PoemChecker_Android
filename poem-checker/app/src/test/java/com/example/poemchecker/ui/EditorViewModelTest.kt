package com.example.poemchecker.ui

import androidx.lifecycle.SavedStateHandle
import com.example.poemchecker.domain.model.*
import com.example.poemchecker.domain.repository.PoemRepository
import com.example.poemchecker.domain.training.FinishRecitation
import com.example.poemchecker.fixtures.FakePoemRepository
import com.example.poemchecker.fixtures.FakeSpeechClient
import com.example.poemchecker.ui.editor.EditorViewModel
import com.example.poemchecker.ui.recitation.RecitationViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun savesAndUpdatesThroughInjectedRepository() = runTest(dispatcher) {
        val repository = FakePoemRepository()
        val editor = EditorViewModel(repository, SavedStateHandle())
        editor.updatePoem("первый стих")
        editor.saveCurrentPoem()
        advanceUntilIdle()
        val id = editor.state.value.currentPoemId!!
        editor.updatePoem("новый текст")
        editor.saveCurrentPoem()
        advanceUntilIdle()
        assertEquals(1, repository.poems.value.size)
        assertEquals("новый текст", repository.getPoem(id)!!.text)
    }

    @Test fun clearingEditorDoesNotDeleteSavedPoem() = runTest(dispatcher) {
        val repository = FakePoemRepository()
        val editor = EditorViewModel(repository, SavedStateHandle())
        editor.updatePoem("стих")
        editor.saveCurrentPoem()
        advanceUntilIdle()
        editor.clearPoem()
        assertEquals("", editor.state.value.poemText)
        assertNull(editor.state.value.currentPoemId)
        assertEquals(1, repository.poems.value.size)
    }

    @Test fun saveSnapshotCannotBeRedirectedToAnotherPoem() = runTest(dispatcher) {
        val repository = FakePoemRepository()
        val editor = EditorViewModel(repository, SavedStateHandle())
        editor.updatePoem("исходный текст")
        editor.saveCurrentPoem()
        editor.clearPoem()
        editor.updatePoem("позднее изменение")
        advanceUntilIdle()
        assertEquals("исходный текст", repository.poems.value.single().text)
        assertEquals("исходный текст", editor.state.value.poemText)
    }

    @Test fun savedPoemIdRestoresTextFromRepository() = runTest(dispatcher) {
        val repository = FakePoemRepository()
        repository.savePoem(Poem(id = 3, text = "из базы"))
        val editor = EditorViewModel(repository, SavedStateHandle(mapOf("poemId" to 3L)))
        advanceUntilIdle()
        assertEquals("из базы", editor.state.value.poemText)
        assertEquals(3L, editor.state.value.currentPoemId)
    }

    @Test fun allModesRemainSelectableAndCancelledDictationAddsNoLine() = runTest(dispatcher) {
        val editor = EditorViewModel(FakePoemRepository(), SavedStateHandle())
        editor.updatePoem("строка")
        editor.appendRecognizedLine("")
        assertEquals("строка", editor.state.value.poemText)
        TrainingMode.values().forEach {
            editor.setTrainingMode(it)
            assertEquals(it, editor.state.value.trainingMode)
        }
    }

    @Test fun editedDraftDoesNotMarkDifferentSavedTextLearned() = runTest(dispatcher) {
        val repository = FakePoemRepository()
        repository.savePoem(Poem(id = 7, text = "старый стих"))
        val editor = EditorViewModel(repository, SavedStateHandle())
        editor.loadPoem(repository.getPoem(7)!!)
        editor.updatePoem("новый текст")
        assertTrue(editor.state.value.hasUnsavedChanges)
        assertNull(editor.state.value.recitationPoemId)

        val speech = FakeSpeechClient()
        val recitation = RecitationViewModel(speech, FinishRecitation(repository), dispatcher)
        fun openExam() {
            val draft = editor.state.value
            assertTrue(recitation.open(draft.recitationPoemId, draft.poemText, TrainingMode.NO_HINTS, SessionKind.EXAM, draft.trainingProgress))
            recitation.startListening()
            speech.emit("новый текст")
            recitation.finish()
        }
        openExam()
        advanceUntilIdle()
        editor.acceptResult(recitation.state.value.result!!)
        assertEquals("старый стих", repository.getPoem(7)!!.text)
        assertFalse(repository.getPoem(7)!!.progress.finalPassed)
        assertTrue(repository.attempts.value.isEmpty())
        assertTrue(editor.state.value.trainingProgress.finalPassed)

        editor.saveCurrentPoem()
        advanceUntilIdle()
        assertEquals("новый текст", repository.getPoem(7)!!.text)
        assertEquals(7L, editor.state.value.recitationPoemId)
        openExam()
        advanceUntilIdle()
        assertEquals(7L, repository.attempts.value.single().poemId)
        assertTrue(repository.getPoem(7)!!.progress.finalPassed)
        recitation.close()
    }

    @Test fun revertingDraftReattachesSavedPoemAndOuterWhitespaceIsIgnored() = runTest(dispatcher) {
        val editor = EditorViewModel(FakePoemRepository(), SavedStateHandle())
        editor.loadPoem(Poem(id = 2, text = "исходный стих"))
        editor.updatePoem("другой стих")
        assertNull(editor.state.value.recitationPoemId)
        editor.updatePoem("  исходный стих\n")
        assertFalse(editor.state.value.hasUnsavedChanges)
        assertEquals(2L, editor.state.value.recitationPoemId)
    }

    @Test fun revertingAnEditedDraftDoesNotTransferItsPracticeResultsToSavedText() = runTest(dispatcher) {
        val repository = FakePoemRepository()
        val savedProgress = TrainingProgress(history = listOf(50), bestScore = 50)
        repository.savePoem(Poem(id = 7, text = "старый стих", progress = savedProgress))
        val editor = EditorViewModel(repository, SavedStateHandle())
        editor.loadPoem(repository.getPoem(7)!!)
        editor.updatePoem("новый текст")
        val speech = FakeSpeechClient()
        val recitation = RecitationViewModel(speech, FinishRecitation(repository), dispatcher)
        recitation.open(editor.state.value.recitationPoemId, editor.state.value.poemText,
            TrainingMode.NO_HINTS, SessionKind.EXAM, editor.state.value.trainingProgress)
        recitation.startListening()
        speech.emit("новый текст")
        recitation.finish()
        advanceUntilIdle()
        editor.acceptResult(recitation.state.value.result!!)
        assertTrue(editor.state.value.trainingProgress.finalPassed)
        editor.updatePoem("старый стих")
        assertEquals(savedProgress, editor.state.value.trainingProgress)
        assertNull(editor.state.value.comparison)
        editor.saveCurrentPoem()
        advanceUntilIdle()
        assertEquals(savedProgress, repository.getPoem(7)!!.progress)
        recitation.close()
    }

    @Test fun returnedDictationWaitsForSavedPoemRestoration() = runTest(dispatcher) {
        val backing = FakePoemRepository()
        backing.savePoem(Poem(id = 3, text = "первая строка"))
        val ready = CompletableDeferred<Unit>()
        val repository = object : PoemRepository by backing {
            override suspend fun getPoem(poemId: Long): Poem? {
                ready.await()
                return backing.getPoem(poemId)
            }
        }
        val editor = EditorViewModel(repository, SavedStateHandle(mapOf("poemId" to 3L)))
        runCurrent()
        editor.appendRecognizedLine("вторая строка")
        editor.appendRecognizedLine("")
        editor.appendRecognizedLine("третья строка")
        ready.complete(Unit)
        advanceUntilIdle()
        assertEquals("первая строка\nвторая строка\nтретья строка", editor.state.value.poemText)
        assertTrue(editor.state.value.hasUnsavedChanges)
        assertFalse(editor.state.value.isDictatingLine)
    }

    @Test fun dictationQueuedDuringSaveRemainsInDraftAndCanBeSavedLater() = runTest(dispatcher) {
        val backing = FakePoemRepository()
        val ready = CompletableDeferred<Unit>()
        val repository = object : PoemRepository by backing {
            override suspend fun savePoem(poem: Poem): Long {
                ready.await()
                return backing.savePoem(poem)
            }
        }
        val editor = EditorViewModel(repository, SavedStateHandle())
        editor.updatePoem("первая строка")
        editor.saveCurrentPoem()
        runCurrent()
        editor.appendRecognizedLine("вторая строка")
        ready.complete(Unit)
        advanceUntilIdle()
        assertEquals("первая строка", backing.poems.value.single().text)
        assertEquals("первая строка\nвторая строка", editor.state.value.poemText)
        assertTrue(editor.state.value.hasUnsavedChanges)
        editor.saveCurrentPoem()
        advanceUntilIdle()
        assertEquals(editor.state.value.poemText, backing.poems.value.single().text)
        assertFalse(editor.state.value.hasUnsavedChanges)
    }

    @Test fun queuedDictationSurvivesSavedStateRestoration() = runTest(dispatcher) {
        val repository = FakePoemRepository()
        repository.savePoem(Poem(id = 3, text = "из базы"))
        val savedState = SavedStateHandle(mapOf(
            "poemId" to 3L,
            "pendingDictation" to arrayListOf("из диктовки")
        ))
        val editor = EditorViewModel(repository, savedState)
        advanceUntilIdle()
        assertEquals("из базы\nиз диктовки", editor.state.value.poemText)
        assertNull(savedState.get<ArrayList<String>>("pendingDictation"))
    }

    @Test fun failedRestorationKeepsQueuedDictationAndReportsTheError() = runTest(dispatcher) {
        val backing = FakePoemRepository()
        val ready = CompletableDeferred<Unit>()
        val repository = object : PoemRepository by backing {
            override suspend fun getPoem(poemId: Long): Poem? {
                ready.await()
                error("Database unavailable")
            }
        }
        val editor = EditorViewModel(repository, SavedStateHandle(mapOf("poemId" to 3L)))
        runCurrent()
        editor.appendRecognizedLine("сохранить эту строку")
        ready.complete(Unit)
        advanceUntilIdle()
        assertEquals("сохранить эту строку", editor.state.value.poemText)
        assertNotNull(editor.state.value.error)
        assertFalse(editor.state.value.isSaving)
    }
}
