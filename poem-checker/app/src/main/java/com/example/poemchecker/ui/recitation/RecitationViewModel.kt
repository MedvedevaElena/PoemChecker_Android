package com.example.poemchecker.ui.recitation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.poemchecker.domain.comparison.*
import com.example.poemchecker.domain.model.*
import com.example.poemchecker.domain.training.FinishRecitation
import com.example.poemchecker.speech.SpeechRecognitionClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RecitationPhase { IDLE, LISTENING, SAVING, FINISHED, ERROR }

data class RecitationUiState(
    val session: SessionSnapshot? = null,
    val recognizedText: String = "",
    val comparison: ComparisonResult? = null,
    val trainingProgress: TrainingProgress = TrainingProgress(),
    val phase: RecitationPhase = RecitationPhase.IDLE,
    val result: RecitationResult? = null,
    val canRetrySave: Boolean = false,
    val error: String? = null
) {
    val poemText get() = session?.referenceText.orEmpty()
    val trainingMode get() = session?.mode ?: TrainingMode.FIRST_WORDS
    val isListening get() = phase == RecitationPhase.LISTENING
    val isSaving get() = phase == RecitationPhase.SAVING
}

class RecitationViewModel(
    private val speech: SpeechRecognitionClient,
    private val finishRecitation: FinishRecitation,
    private val computation: CoroutineDispatcher = Dispatchers.Default,
    private val compare: (String, String) -> ComparisonResult = PoemComparator::compare
) : ViewModel() {
    private val _state = MutableStateFlow(RecitationUiState())
    val state = _state.asStateFlow()
    private var nextSessionId = 0L
    private var revision = 0L
    private var comparisonJob: Job? = null

    fun open(poemId: Long?, text: String, mode: TrainingMode, kind: SessionKind, progress: TrainingProgress): Boolean {
        if (_state.value.isSaving || PoemComparator.lineWordCounts(text).isEmpty()) return false
        cancelListening()
        val session = SessionSnapshot(++nextSessionId, poemId, text, if (kind == SessionKind.EXAM) TrainingMode.NO_HINTS else mode, kind)
        _state.value = RecitationUiState(session = session, trainingProgress = progress)
        showInitialComparison()
        return true
    }

    private fun showInitialComparison() {
        val session = _state.value.session ?: return
        updateComparison(session, "", mayFinish = false)
    }

    fun startListening() {
        val before = _state.value
        if (before.isSaving || before.isListening) return
        val previous = before.session ?: return
        comparisonJob?.cancel()
        val session = previous.copy(
            id = ++nextSessionId,
            mode = before.result?.update?.nextMode ?: previous.mode
        )
        ++revision
        _state.value = before.copy(session = session, phase = RecitationPhase.LISTENING, recognizedText = "", comparison = null, result = null, canRetrySave = false, error = null)
        showInitialComparison()
        speech.start(
            onText = { text ->
                if (_state.value.session?.id == session.id && _state.value.isListening) {
                    updateComparison(session, text, mayFinish = true)
                }
            },
            onError = { message ->
                if (_state.value.session?.id == session.id && _state.value.isListening) {
                    cancelListening()
                    _state.value = _state.value.copy(phase = RecitationPhase.ERROR, error = message)
                }
            }
        )
    }

    private fun updateComparison(session: SessionSnapshot, text: String, mayFinish: Boolean) {
        comparisonJob?.cancel()
        val inputRevision = ++revision
        _state.value = _state.value.copy(recognizedText = text)
        comparisonJob = viewModelScope.launch {
            val comparison = withContext(computation) { compare(session.referenceText, text) }
            if (inputRevision != revision || session.id != _state.value.session?.id) return@launch
            _state.value = _state.value.copy(comparison = comparison)
            if (mayFinish && _state.value.isListening && session.kind == SessionKind.TRAINING && comparison.isComplete) {
                finish()
            }
        }
    }

    fun finish() {
        val snapshot = _state.value
        val session = snapshot.session ?: return
        if (!snapshot.isListening && !snapshot.canRetrySave) return
        val text = if (snapshot.isListening) speech.stop() else snapshot.recognizedText
        comparisonJob?.cancel()
        ++revision
        _state.value = snapshot.copy(recognizedText = text, phase = RecitationPhase.SAVING, canRetrySave = false, error = null)
        viewModelScope.launch {
            try {
                val comparison = withContext(computation) { compare(session.referenceText, text) }
                val update = finishRecitation(session, text, comparison, snapshot.trainingProgress)
                _state.value = _state.value.copy(
                    comparison = comparison,
                    trainingProgress = update.progress,
                    phase = RecitationPhase.FINISHED,
                    result = RecitationResult(session, text, comparison, update)
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = _state.value.copy(phase = RecitationPhase.ERROR, canRetrySave = true, error = "Не удалось сохранить попытку. Повторите сохранение.")
            }
        }
    }

    /** Leaving/backgrounding cancels capture, never creates a completed attempt. */
    fun cancelListening() {
        speech.cancel()
        comparisonJob?.cancel()
        ++revision
        if (_state.value.isListening) _state.value = _state.value.copy(phase = RecitationPhase.IDLE)
    }

    fun showError(message: String) {
        cancelListening()
        _state.value = _state.value.copy(phase = RecitationPhase.ERROR, error = message)
    }

    fun close(): Boolean {
        if (_state.value.isSaving) return false
        cancelListening()
        _state.value = RecitationUiState()
        return true
    }

    override fun onCleared() { speech.cancel() }
}
