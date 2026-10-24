package com.example.poemchecker.ui

import androidx.lifecycle.SavedStateHandle
import com.example.poemchecker.domain.model.*
import com.example.poemchecker.fixtures.FakePoemRepository
import com.example.poemchecker.ui.editor.EditorViewModel
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
}
