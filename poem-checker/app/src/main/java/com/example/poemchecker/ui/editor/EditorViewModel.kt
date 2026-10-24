package com.example.poemchecker.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.poemchecker.domain.comparison.ComparisonResult
import com.example.poemchecker.domain.model.*
import com.example.poemchecker.domain.repository.PoemRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class EditorUiState(
    val poemText: String = "",
    val currentPoemId: Long? = null,
    val savedPoemText: String? = null,
    val trainingMode: TrainingMode = TrainingMode.FIRST_WORDS,
    val trainingProgress: TrainingProgress = TrainingProgress(),
    val savedProgress: TrainingProgress = TrainingProgress(),
    val recognizedText: String = "",
    val comparison: ComparisonResult? = null,
    val isDictatingLine: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
) {
    val hasUnsavedChanges: Boolean
        get() = currentPoemId != null && poemText.trim() != savedPoemText
    val recitationPoemId: Long?
        get() = currentPoemId.takeUnless { hasUnsavedChanges }
}

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModel(
    private val repository: PoemRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val _state = MutableStateFlow(EditorUiState())
    val state = _state.asStateFlow()
    val savedPoems = repository.observePoems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val attempts = state.map { it.currentPoemId }.distinctUntilChanged().flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeAttempts(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        savedState.get<Long>("poemId")?.let { id ->
            // Loading is a save-like operation: don't replace user input while restoration is pending.
            _state.value = _state.value.copy(isSaving = true)
            launchOperation {
                repository.getPoem(id)?.let { applyPoem(it) }
            }
        }
        if (!_state.value.isSaving) drainPendingDictation()
    }

    fun updatePoem(text: String) {
        if (!_state.value.isSaving) {
            setDraftText(text)
            _state.value = _state.value.copy(error = null)
        }
    }

    private fun setDraftText(text: String) {
        val old = _state.value
        val changed = old.poemText.trim() != text.trim()
        _state.value = old.copy(
            poemText = text,
            trainingProgress = if (changed) old.savedProgress else old.trainingProgress,
            recognizedText = if (changed) "" else old.recognizedText,
            comparison = if (changed) null else old.comparison
        )
    }

    fun setDictatingLine(value: Boolean) { _state.value = _state.value.copy(isDictatingLine = value) }
    fun setTrainingMode(mode: TrainingMode) { _state.value = _state.value.copy(trainingMode = mode) }
    fun showError(message: String) { _state.value = _state.value.copy(error = message, isDictatingLine = false) }

    fun appendRecognizedLine(text: String) {
        if (text.isNotBlank()) {
            if (_state.value.isSaving) {
                val pending = savedState.get<ArrayList<String>>("pendingDictation").orEmpty()
                savedState["pendingDictation"] = ArrayList(pending + text)
            } else {
                appendLine(text)
            }
        }
        setDictatingLine(false)
    }

    private fun appendLine(text: String) {
        val old = _state.value.poemText
        setDraftText(if (old.isBlank()) text else "$old\n$text")
    }

    private fun drainPendingDictation() {
        val pending = savedState.remove<ArrayList<String>>("pendingDictation").orEmpty()
        pending.forEach(::appendLine)
    }

    fun saveCurrentPoem() {
        val snapshot = _state.value
        if (snapshot.isSaving || snapshot.poemText.isBlank()) return
        _state.value = snapshot.copy(isSaving = true, error = null)
        launchOperation {
            val id = repository.savePoem(Poem(
                id = snapshot.currentPoemId ?: 0,
                text = snapshot.poemText,
                progress = snapshot.trainingProgress
            ))
            savedState["poemId"] = id
            _state.value = _state.value.copy(
                currentPoemId = id,
                savedPoemText = snapshot.poemText.trim(),
                savedProgress = snapshot.trainingProgress
            )
        }
    }

    fun loadPoem(poem: Poem) {
        if (!_state.value.isSaving) applyPoem(poem)
    }

    private fun applyPoem(poem: Poem) {
        savedState["poemId"] = poem.id
        _state.value = EditorUiState(
            poemText = poem.text,
            currentPoemId = poem.id,
            savedPoemText = poem.text.trim(),
            trainingMode = when {
                poem.progress.finalPassed || poem.progress.lastWordsPassed -> TrainingMode.NO_HINTS
                poem.progress.firstWordsPassed -> TrainingMode.LAST_WORDS
                else -> TrainingMode.FIRST_WORDS
            },
            trainingProgress = poem.progress,
            savedProgress = poem.progress
        )
    }

    fun clearPoem() {
        if (_state.value.isSaving) return
        savedState.remove<Long>("poemId")
        savedState.remove<ArrayList<String>>("pendingDictation")
        _state.value = EditorUiState()
    }

    fun deletePoem(poem: Poem) {
        if (_state.value.isSaving) return
        _state.value = _state.value.copy(isSaving = true, error = null)
        launchOperation {
            repository.deletePoem(poem.id)
            if (_state.value.currentPoemId == poem.id) {
                savedState.remove<Long>("poemId")
                _state.value = EditorUiState(isSaving = true)
            }
        }
    }

    fun acceptResult(result: RecitationResult) {
        if (_state.value.recitationPoemId != result.session.poemId || _state.value.poemText != result.session.referenceText) return
        _state.value = _state.value.copy(
            trainingProgress = result.update.progress,
            savedProgress = if (result.session.poemId != null) result.update.progress else _state.value.savedProgress,
            trainingMode = result.update.nextMode,
            recognizedText = result.recognizedText,
            comparison = result.comparison
        )
    }

    private fun launchOperation(operation: suspend () -> Unit) = viewModelScope.launch {
        try {
            operation()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            showError("Не удалось сохранить или загрузить стих. Попробуйте ещё раз.")
        } finally {
            _state.value = _state.value.copy(isSaving = false)
            drainPendingDictation()
        }
    }
}
