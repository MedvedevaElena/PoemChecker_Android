package com.example.poemchecker

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

enum class TrainingMode {
    FIRST_WORDS,
    LAST_WORDS,
    NO_HINTS
}

enum class ScreenMode {
    EDITOR,
    TRAINING,
    EXAM
}

data class LiveWordUi(
    val expected: String?,
    val actual: String?,
    val state: WordState,
    val isHint: Boolean = false
)

enum class WordState {
    MATCH,
    WRONG,
    MISSING,
    EXTRA
}

data class TrainingProgress(
    val firstWordsUnlocked: Boolean = true,
    val lastWordsUnlocked: Boolean = true,
    val noHintsUnlocked: Boolean = true,
    val firstWordsPassed: Boolean = false,
    val lastWordsPassed: Boolean = false,
    val finalPassed: Boolean = false,
    val history: List<Int> = emptyList(),
    val bestScore: Int = 0
)

data class PoemEditorState(
    val poemText: String = "",
    val recognizedText: String = "",
    val isListening: Boolean = false,
    val isDictatingLine: Boolean = false,
    val trainingMode: TrainingMode = TrainingMode.FIRST_WORDS,
    val currentPoemId: Long? = null,
    val trainingProgress: TrainingProgress = TrainingProgress(),
    val comparison: ComparisonResult? = null,
    val screenMode: ScreenMode = ScreenMode.EDITOR
)

@Entity(tableName = "poems")
data class SavedPoem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val text: String,
    val firstWordsPassed: Boolean = false,
    val lastWordsPassed: Boolean = false,
    val noHintsUnlocked: Boolean = false,
    val finalPassed: Boolean = false,
    val scoreHistory: String = "",
    val bestScore: Int = 0
)

@Entity(tableName = "attempts")
data class AttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val poemId: Long,
    val mode: String,
    val scorePercent: Int,
    val createdAt: Long,
    val recognizedPreview: String
)

/** True once every expected word has been accounted for (matched or mismatched) — i.e. the last word was said. */
private fun isComparisonComplete(comparison: ComparisonResult): Boolean {
    if (comparison.expectedCount == 0) return false
    return comparison.alignedWords.none { it.expected != null && it.actual == null }
}

private fun toLiveWordUi(pair: AlignedWord, isHint: Boolean): LiveWordUi {
    return when {
        pair.expected == pair.actual && pair.expected != null ->
            LiveWordUi(expected = pair.expected, actual = pair.actual, state = WordState.MATCH, isHint = isHint)

        pair.expected != null && pair.actual != null ->
            LiveWordUi(expected = pair.expected, actual = pair.actual, state = WordState.WRONG, isHint = isHint)

        pair.expected != null && pair.actual == null ->
            LiveWordUi(expected = pair.expected, actual = null, state = WordState.MISSING, isHint = isHint)

        else ->
            LiveWordUi(expected = null, actual = pair.actual, state = WordState.EXTRA)
    }
}

/**
 * Groups the live comparison by poem line and marks, within each line, the word that acts as the
 * hint for the current mode (first word / last word) so it can be rendered as its own leaf that's
 * always visible — blue until it's actually said, green once matched — merged into the same flow
 * as the ordinary recognized-word leaves instead of a separate hint block.
 */
private fun buildLiveLines(
    comparison: ComparisonResult,
    poemText: String,
    mode: TrainingMode
): List<List<LiveWordUi>> {
    val lineWordCounts = PoemComparator.lineWordCounts(poemText)
    if (lineWordCounts.isEmpty()) {
        return listOf(comparison.alignedWords.map { toLiveWordUi(it, isHint = false) })
    }

    val lineStarts = lineWordCounts.runningFold(0) { acc, count -> acc + count }
    val hintIndices: Set<Int> = when (mode) {
        TrainingMode.FIRST_WORDS -> lineStarts.dropLast(1).toSet()
        TrainingMode.LAST_WORDS -> lineStarts.drop(1).map { it - 1 }.toSet()
        TrainingMode.NO_HINTS -> emptySet()
    }

    val lines = MutableList(lineWordCounts.size) { mutableListOf<LiveWordUi>() }
    var lineIndex = 0
    var consumedInLine = 0
    var globalExpectedIndex = 0
    comparison.alignedWords.forEach { pair ->
        val bucketIndex = lineIndex.coerceAtMost(lines.size - 1)
        val isHint = pair.expected != null && globalExpectedIndex in hintIndices
        lines[bucketIndex].add(toLiveWordUi(pair, isHint))
        if (pair.expected != null) {
            globalExpectedIndex++
            consumedInLine++
            if (consumedInLine >= lineWordCounts[bucketIndex] && lineIndex < lines.size - 1) {
                lineIndex++
                consumedInLine = 0
            }
        }
    }
    return lines
}

@Dao
interface PoemDao {
    @Query("SELECT * FROM poems ORDER BY id DESC")
    fun observeAll(): Flow<List<SavedPoem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(poem: SavedPoem): Long

    @Query("DELETE FROM poems WHERE id = :poemId")
    suspend fun deleteById(poemId: Long)

    @Query("SELECT * FROM poems WHERE id = :poemId LIMIT 1")
    suspend fun getById(poemId: Long): SavedPoem?
}

@Dao
interface AttemptDao {
    @Query("SELECT * FROM attempts WHERE poemId = :poemId ORDER BY createdAt DESC LIMIT 20")
    fun observeForPoem(poemId: Long): Flow<List<AttemptEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attempt: AttemptEntity)

    @Query("DELETE FROM attempts WHERE poemId = :poemId")
    suspend fun deleteForPoem(poemId: Long)
}

@Database(entities = [SavedPoem::class, AttemptEntity::class], version = 1, exportSchema = false)
abstract class PoemDatabase : RoomDatabase() {
    abstract fun poemDao(): PoemDao
    abstract fun attemptDao(): AttemptDao

    companion object {
        @Volatile private var instance: PoemDatabase? = null

        fun getInstance(application: Application): PoemDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(application, PoemDatabase::class.java, "poem_checker_v9.db").build().also { instance = it }
            }
        }
    }
}

class PoemRepository(
    private val poemDao: PoemDao,
    private val attemptDao: AttemptDao
) {
    fun observePoems() = poemDao.observeAll()
    fun observeAttempts(poemId: Long) = attemptDao.observeForPoem(poemId)

    suspend fun savePoem(text: String): Long {
        val title = text.lineSequence().firstOrNull()?.take(40).orEmpty().ifBlank { "Без названия" }
        return poemDao.insert(SavedPoem(title = title, text = text))
    }

    suspend fun updatePoem(poem: SavedPoem): Long = poemDao.insert(poem)
    suspend fun deletePoem(poemId: Long) {
        attemptDao.deleteForPoem(poemId)
        poemDao.deleteById(poemId)
    }
    suspend fun getPoem(poemId: Long): SavedPoem? = poemDao.getById(poemId)
    suspend fun insertAttempt(attempt: AttemptEntity) = attemptDao.insert(attempt)
}

class PoemCheckerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PoemRepository(
        PoemDatabase.getInstance(application).poemDao(),
        PoemDatabase.getInstance(application).attemptDao()
    )
    private val _editorState = MutableStateFlow(PoemEditorState())
    val editorState: StateFlow<PoemEditorState> = _editorState.asStateFlow()

    val savedPoems = repository.observePoems().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val attempts = editorState.flatMapLatest { state ->
        val id = state.currentPoemId
        if (id == null) flowOf(emptyList()) else repository.observeAttempts(id)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun openTrainingScreen() {
        val text = _editorState.value.poemText
        if (text.isBlank()) return
        _editorState.value = _editorState.value.copy(
            screenMode = ScreenMode.TRAINING,
            recognizedText = "",
            // Compare against nothing said yet so hint leaves are visible right away,
            // before the recognizer has produced a single partial result.
            comparison = PoemComparator.compare(text, "")
        )
    }

    fun closeTrainingScreen() {
        _editorState.value = _editorState.value.copy(
            screenMode = ScreenMode.EDITOR,
            isListening = false
        )
    }

    fun updatePoem(text: String) {
        _editorState.value = _editorState.value.copy(poemText = text)
    }

    fun setListening(value: Boolean) {
        _editorState.value = _editorState.value.copy(isListening = value)
    }

    fun setDictatingLine(value: Boolean) {
        _editorState.value = _editorState.value.copy(isDictatingLine = value)
    }

    fun setTrainingMode(mode: TrainingMode) {
        _editorState.value = _editorState.value.copy(trainingMode = mode)
    }

    fun openExamScreen() {
        if (_editorState.value.trainingProgress.noHintsUnlocked) {
            _editorState.value = _editorState.value.copy(screenMode = ScreenMode.EXAM, trainingMode = TrainingMode.NO_HINTS)
        }
    }

    fun closeExamScreen() {
        _editorState.value = _editorState.value.copy(screenMode = ScreenMode.EDITOR)
    }

    fun appendRecognizedLine(line: String) {
        val current = _editorState.value.poemText
        val newText = if (current.isBlank()) line else current + "\n" + line
        _editorState.value = _editorState.value.copy(poemText = newText, isDictatingLine = false)
    }

    fun updateLiveRecognizedText(recognizedText: String) {
        val comparison = PoemComparator.compare(_editorState.value.poemText, recognizedText)
        _editorState.value = _editorState.value.copy(recognizedText = recognizedText, comparison = comparison)
    }

    fun finishRecognition(recognizedText: String) {
        val comparison = PoemComparator.compare(_editorState.value.poemText, recognizedText)
        val updatedProgress = unlockNextMode(_editorState.value.trainingMode, comparison.scorePercent, _editorState.value.trainingProgress)
        // Auto-advance only on an actual pass this attempt — *Unlocked flags are now always true
        // (all modes are freely selectable), so they can no longer gate this; *Passed is score-gated.
        val nextMode = when {
            _editorState.value.trainingMode == TrainingMode.FIRST_WORDS && updatedProgress.firstWordsPassed -> TrainingMode.LAST_WORDS
            _editorState.value.trainingMode == TrainingMode.LAST_WORDS && updatedProgress.lastWordsPassed -> TrainingMode.NO_HINTS
            else -> _editorState.value.trainingMode
        }
        _editorState.value = _editorState.value.copy(
            recognizedText = recognizedText,
            comparison = comparison,
            isListening = false,
            trainingProgress = updatedProgress,
            trainingMode = nextMode
        )
        persistProgressIfPossible()
        saveAttemptIfPossible(recognizedText, comparison.scorePercent)
    }

    private fun unlockNextMode(mode: TrainingMode, scorePercent: Int, progress: TrainingProgress): TrainingProgress {
        val newHistory = (progress.history + scorePercent).takeLast(20)
        val newBest = max(progress.bestScore, scorePercent)
        return when (mode) {
            TrainingMode.FIRST_WORDS -> {
                if (scorePercent >= 85) progress.copy(
                    lastWordsUnlocked = true,
                    firstWordsPassed = true,
                    history = newHistory,
                    bestScore = newBest
                ) else progress.copy(history = newHistory, bestScore = newBest)
            }
            TrainingMode.LAST_WORDS -> {
                if (scorePercent >= 85) progress.copy(
                    noHintsUnlocked = true,
                    lastWordsPassed = true,
                    history = newHistory,
                    bestScore = newBest
                ) else progress.copy(history = newHistory, bestScore = newBest)
            }
            TrainingMode.NO_HINTS -> {
                if (scorePercent >= 90) progress.copy(
                    finalPassed = true,
                    history = newHistory,
                    bestScore = newBest
                ) else progress.copy(history = newHistory, bestScore = newBest)
            }
        }
    }

    fun saveCurrentPoem() {
        val text = _editorState.value.poemText.trim()
        if (text.isBlank()) return
        viewModelScope.launch {
            val existingId = _editorState.value.currentPoemId
            if (existingId == null) {
                val newId = repository.savePoem(text)
                _editorState.value = _editorState.value.copy(currentPoemId = newId)
                persistProgressIfPossible()
            } else {
                persistProgressIfPossible(textOverride = text)
            }
        }
    }

    fun loadPoem(poem: SavedPoem) {
        _editorState.value = _editorState.value.copy(
            poemText = poem.text,
            recognizedText = "",
            comparison = null,
            currentPoemId = poem.id,
            screenMode = ScreenMode.EDITOR,
            trainingMode = when {
                poem.finalPassed || poem.noHintsUnlocked -> TrainingMode.NO_HINTS
                poem.lastWordsPassed -> TrainingMode.LAST_WORDS
                else -> TrainingMode.FIRST_WORDS
            },
            trainingProgress = TrainingProgress(
                firstWordsUnlocked = true,
                lastWordsUnlocked = true,
                noHintsUnlocked = true,
                firstWordsPassed = poem.firstWordsPassed,
                lastWordsPassed = poem.lastWordsPassed,
                finalPassed = poem.finalPassed,
                history = poem.scoreHistory.split(',').mapNotNull { it.toIntOrNull() },
                bestScore = poem.bestScore
            )
        )
    }

    fun deletePoem(poem: SavedPoem) {
        viewModelScope.launch {
            repository.deletePoem(poem.id)
            if (_editorState.value.currentPoemId == poem.id) clearPoem()
        }
    }

    fun clearPoem() {
        _editorState.value = PoemEditorState()
    }

    private fun persistProgressIfPossible(textOverride: String? = null) {
        val poemId = _editorState.value.currentPoemId ?: return
        val poemText = textOverride ?: _editorState.value.poemText.trim()
        if (poemText.isBlank()) return
        val state = _editorState.value
        viewModelScope.launch {
            val old = repository.getPoem(poemId) ?: return@launch
            val updated = old.copy(
                text = poemText,
                title = poemText.lineSequence().firstOrNull()?.take(40).orEmpty().ifBlank { "Без названия" },
                firstWordsPassed = state.trainingProgress.firstWordsPassed,
                lastWordsPassed = state.trainingProgress.lastWordsPassed,
                noHintsUnlocked = state.trainingProgress.noHintsUnlocked,
                finalPassed = state.trainingProgress.finalPassed,
                scoreHistory = state.trainingProgress.history.joinToString(","),
                bestScore = state.trainingProgress.bestScore
            )
            repository.updatePoem(updated)
        }
    }

    private fun saveAttemptIfPossible(recognizedText: String, scorePercent: Int) {
        val poemId = _editorState.value.currentPoemId ?: return
        val mode = _editorState.value.trainingMode.name
        viewModelScope.launch {
            repository.insertAttempt(
                AttemptEntity(
                    poemId = poemId,
                    mode = mode,
                    scorePercent = scorePercent,
                    createdAt = System.currentTimeMillis(),
                    recognizedPreview = recognizedText.take(120)
                )
            )
        }
    }
}

/**
 * ACTION_RECOGNIZE_SPEECH ends a segment after a short silence, which would normally stop
 * dictation on any pause. To keep listening continuous until the user explicitly stops it,
 * every finished segment (onResults) or a silence/no-match error is treated as "keep going":
 * the recognized text is appended to [finalizedText] and a new segment is started right away.
 * Only [stop] ends dictation for real.
 */
private class LiveSpeechRecognizer(
    private val context: Context,
    private val onProgress: (String) -> Unit,
    private val isComplete: () -> Boolean,
    private val onAutoStopped: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null
    private var shouldContinue = false
    private var finalizedText = ""
    private var lastPartial = ""

    private fun combine(a: String, b: String) =
        listOf(a, b).filter { it.isNotBlank() }.joinToString(" ")

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("На устройстве нет поддержки SpeechRecognizer")
            return
        }
        stopInternal()
        shouldContinue = true
        finalizedText = ""
        lastPartial = ""
        startSegment()
    }

    /** Reports progress and, if the caller now considers the poem fully spoken, stops for real. */
    private fun reportProgress(text: String) {
        onProgress(text)
        if (shouldContinue && isComplete()) {
            shouldContinue = false
            stopInternal()
            onAutoStopped(text)
        }
    }

    private fun startSegment() {
        if (!shouldContinue) return
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    lastPartial = text
                    reportProgress(combine(finalizedText, lastPartial))
                }
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (text.isNotBlank()) {
                        finalizedText = combine(finalizedText, text)
                    }
                    lastPartial = ""
                    reportProgress(finalizedText)
                    if (shouldContinue) startSegment()
                }
                override fun onError(error: Int) {
                    if (shouldContinue &&
                        (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                    ) {
                        startSegment()
                    } else {
                        shouldContinue = false
                        onError("Ошибка распознавания: $error")
                    }
                }
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra("android.speech.extra.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 4000)
                putExtra("android.speech.extra.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 4000)
                putExtra("android.speech.extra.SPEECH_INPUT_MINIMUM_LENGTH_MILLIS", 15000)
            }

            startListening(intent)
        }
    }

    /** Stops dictation and returns everything recognized so far. */
    fun stop(): String {
        shouldContinue = false
        val result = combine(finalizedText, lastPartial)
        stopInternal()
        return result
    }

    private fun stopInternal() {
        recognizer?.stopListening()
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PoemCheckerApp()
                }
            }
        }
    }
}

@Composable
private fun PoemCheckerApp(viewModel: PoemCheckerViewModel = viewModel()) {
    val context = LocalContext.current
    val editorState by viewModel.editorState.collectAsState()
    val savedPoems by viewModel.savedPoems.collectAsState()
    val attempts by viewModel.attempts.collectAsState()

    val speechRecognizer = remember {
        LiveSpeechRecognizer(
            context = context,
            onProgress = { viewModel.updateLiveRecognizedText(it) },
            isComplete = {
                val current = viewModel.editorState.value
                current.screenMode == ScreenMode.TRAINING &&
                    current.comparison?.let { isComparisonComplete(it) } == true
            },
            onAutoStopped = { finalText -> viewModel.finishRecognition(finalText) },
            onError = {
                viewModel.setListening(false)
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose { speechRecognizer.stop() }
    }

    val lineVoiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
        viewModel.appendRecognizedLine(spokenText)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            viewModel.setListening(false)
            viewModel.setDictatingLine(false)
            Toast.makeText(context, "Нужен доступ к микрофону", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        if (editorState.isDictatingLine) {
            startLineRecognition(lineVoiceLauncher::launch) {
                viewModel.setDictatingLine(false)
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            }
        } else {
            viewModel.setListening(true)
            speechRecognizer.start()
        }
    }

    // Checks the mic permission, then either starts dictation right away or requests it first.
    val beginListening: () -> Unit = {
        // Drop any leftover match/wrong words from a previous attempt — only hint leaves remain.
        viewModel.updateLiveRecognizedText("")
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.setListening(true)
        if (granted) {
            speechRecognizer.start()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    if (editorState.screenMode == ScreenMode.TRAINING) {
        TrainingScreen(
            state = editorState,
            onBack = {
                speechRecognizer.stop()
                viewModel.setListening(false)
                viewModel.closeTrainingScreen()
            },
            onToggleListening = {
                if (editorState.isListening) {
                    val finalText = speechRecognizer.stop()
                    viewModel.finishRecognition(finalText)
                } else {
                    beginListening()
                }
            }
        )
        return
    }

    if (editorState.screenMode == ScreenMode.EXAM) {
        ExamScreen(
            state = editorState,
            attempts = attempts,
            onBack = viewModel::closeExamScreen,
            onStart = {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    if (editorState.isListening) {
                        val finalText = speechRecognizer.stop()
                        viewModel.finishRecognition(finalText)
                    } else {
                        viewModel.setListening(true)
                        speechRecognizer.start()
                    }
                } else {
                    viewModel.setListening(true)
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        )
        return
    }

    val hints = buildHints(editorState.poemText, editorState.trainingMode)

    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Poem Checker v9", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            item {
                Text("Добавлены полноценная история попыток в базе и отдельный экран экзамена без подсказок.")
            }
            item {
                OutlinedTextField(
                    value = editorState.poemText,
                    onValueChange = viewModel::updatePoem,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Текст стиха") },
                    minLines = 8,
                    shape = RoundedCornerShape(16.dp)
                )
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                viewModel.setDictatingLine(true)
                                startLineRecognition(lineVoiceLauncher::launch) {
                                    viewModel.setDictatingLine(false)
                                    Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                                }
                            } else {
                                viewModel.setDictatingLine(true)
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text(if (editorState.isDictatingLine) "Слушаю строку…" else "Надиктовать строку") }

                    Button(
                        onClick = viewModel::clearPoem,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("Очистить") }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = viewModel::saveCurrentPoem,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text(if (editorState.currentPoemId == null) "Сохранить стих" else "Обновить стих") }

                    Button(
                        onClick = {
                            if (editorState.poemText.isBlank()) {
                                Toast.makeText(context, "Сначала задайте текст стиха", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            viewModel.openTrainingScreen()
                            beginListening()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Начать тренировку")
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = viewModel::openExamScreen,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        enabled = editorState.trainingProgress.noHintsUnlocked
                    ) { Text("Открыть экран экзамена") }
                }
            }
            item {
                TrainingModeCard(editorState.trainingMode, editorState.trainingProgress, viewModel::setTrainingMode)
            }
            item {
                TrainingProgressCard(editorState.trainingProgress, editorState.comparison, editorState.trainingMode)
            }
            if (hints.isNotEmpty()) {
                item { HintCard(editorState.trainingMode, hints) }
            }
            if (savedPoems.isNotEmpty()) {
                item {
                    Text("Сохранённые стихи", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                items(savedPoems) { poem ->
                    SavedPoemCard(poem = poem, onLoad = { viewModel.loadPoem(poem) }, onDelete = { viewModel.deletePoem(poem) })
                }
            }
            if (attempts.isNotEmpty()) {
                item {
                    AttemptsCard(attempts)
                }
            }
            if (editorState.recognizedText.isNotBlank()) {
                item {
                    InfoCard(if (editorState.isListening) "Распознано сейчас" else "Финально распознано") { Text(editorState.recognizedText) }
                }
            }
            editorState.comparison?.let { comparison ->
                item { DiffCard(comparison, live = editorState.isListening) }
                item { AnalysisCard(comparison) }
            }
        }
    }
}

@Composable
private fun LiveWordCard(word: LiveWordUi) {
    if (word.isHint && word.state == WordState.MISSING) {
        // The hint word for this line hasn't been said yet — always visible, blue, no "пропущено" label.
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFD6E4FF))
        ) {
            Text(
                text = word.expected.orEmpty(),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                color = Color(0xFF1A3E8C),
                fontWeight = FontWeight.SemiBold
            )
        }
        return
    }

    when (word.state) {
        WordState.MATCH -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFDDF5E3))
            ) {
                Text(
                    text = word.actual.orEmpty(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    color = Color(0xFF1B5E20),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        WordState.WRONG -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFDAD6))
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                        text = word.expected.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF7A1C1C)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = word.actual.orEmpty(),
                        color = Color(0xFFB3261E),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        WordState.MISSING -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE5E5))
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                        text = word.expected.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF7A1C1C),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "пропущено",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFB3261E)
                    )
                }
            }
        }

        WordState.EXTRA -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0C2))
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                        text = "лишнее",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF7A5A00)
                    )
                    Text(
                        text = word.actual.orEmpty(),
                        color = Color(0xFF7A5A00),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}


@Composable
private fun ExamScreen(
    state: PoemEditorState,
    attempts: List<AttemptEntity>,
    onBack: () -> Unit,
    onStart: () -> Unit
) {
    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                    Column {
                        Text("Экзамен без подсказок", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Подсказки скрыты. Для зачёта нужно 90% и выше.")
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Текущий лучший результат: ${state.trainingProgress.bestScore}%")
                        Text(if (state.trainingProgress.finalPassed) "Зачёт уже получен" else "Зачёт ещё не получен")
                        Button(onClick = onStart, shape = RoundedCornerShape(16.dp)) {
                            Text(if (state.isListening) "Остановить экзамен" else "Начать экзамен")
                        }
                    }
                }
            }
            state.comparison?.let { comparison ->
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Последний результат экзамена", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Совпадение: ${comparison.scorePercent}%")
                            Text(if (comparison.scorePercent >= 90) "Экзамен сдан" else "Экзамен не сдан")
                        }
                    }
                }
            }
            if (attempts.isNotEmpty()) {
                item { AttemptsCard(attempts) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrainingScreen(
    state: PoemEditorState,
    onBack: () -> Unit,
    onToggleListening: () -> Unit
) {
    val liveLines = state.comparison?.let { buildLiveLines(it, state.poemText, state.trainingMode) }.orEmpty()
    // While actively dictating, only show words that were actually said (match/wrong) — except the
    // hint word for each line, which stays visible (blue, then green once said) merged into the flow.
    val displayedLines = if (state.isListening) {
        liveLines.map { line -> line.filter { it.isHint || it.state != WordState.MISSING } }
    } else {
        liveLines
    }
    val hasAnyWords = displayedLines.any { it.isNotEmpty() }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Закрыть")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Тренировка",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when (state.trainingMode) {
                            TrainingMode.FIRST_WORDS -> "Режим: первые слова"
                            TrainingMode.LAST_WORDS -> "Режим: последние слова"
                            TrainingMode.NO_HINTS -> "Режим: без подсказок"
                        }
                    )
                }
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onToggleListening,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(if (state.isListening) "Остановить" else "Начать говорить")
                }
                Button(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Завершить")
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (state.isListening) "Распознаю в реальном времени" else "Нажми и начни читать",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (!hasAnyWords) {
                            Text("Здесь по мере речи будут появляться распознанные слова и подсказки.")
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                displayedLines.forEach { line ->
                                    if (line.isNotEmpty()) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            line.forEach { word -> LiveWordCard(word) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            state.comparison?.let { comparison ->
                if (state.isListening) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Текущий результат",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text("Совпадение: ${comparison.scorePercent}%")
                                Text("Совпавших слов: ${comparison.matchedCount} из ${comparison.expectedCount}")
                            }
                        }
                    }
                } else {
                    // Dictation was stopped manually — now it's safe to reveal the full analysis,
                    // including missing/extra words, since it's no longer "live".
                    item { AnalysisCard(comparison) }
                }
            }
        }
    }
}

@Composable
private fun SavedPoemCard(poem: SavedPoem, onLoad: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onLoad() }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(poem.title, fontWeight = FontWeight.SemiBold)
                Text(poem.text.take(120))
                Text(
                    when {
                        poem.finalPassed -> "Статус: стих выучен"
                        poem.noHintsUnlocked -> "Статус: доступен экзамен"
                        poem.lastWordsPassed -> "Статус: пройдены последние слова"
                        poem.firstWordsPassed -> "Статус: открыты последние слова"
                        else -> "Статус: начало обучения"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                Text("Лучший результат: ${poem.bestScore}%", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Удалить стих") }
        }
    }
}

@Composable
private fun TrainingModeCard(selectedMode: TrainingMode, progress: TrainingProgress, onModeSelected: (TrainingMode) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Режимы тренировки", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TrainingMode.values().forEach { mode ->
                    val label = when (mode) {
                        TrainingMode.FIRST_WORDS -> "Первые"
                        TrainingMode.LAST_WORDS -> "Последние"
                        TrainingMode.NO_HINTS -> "Без подсказки"
                    }
                    val unlocked = when (mode) {
                        TrainingMode.FIRST_WORDS -> true
                        TrainingMode.LAST_WORDS -> progress.lastWordsUnlocked
                        TrainingMode.NO_HINTS -> progress.noHintsUnlocked
                    }
                    AssistChip(onClick = { if (unlocked) onModeSelected(mode) }, label = { Text(if (unlocked) label else "$label 🔒") })
                }
            }
            Text(
                when (selectedMode) {
                    TrainingMode.FIRST_WORDS -> "Сейчас видны только первые слова строк."
                    TrainingMode.LAST_WORDS -> "Сейчас видны только последние слова строк."
                    TrainingMode.NO_HINTS -> "Подсказки отключены. Можно тренироваться или сдавать экзамен."
                }
            )
        }
    }
}

@Composable
private fun TrainingProgressCard(progress: TrainingProgress, comparison: ComparisonResult?, currentMode: TrainingMode) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Прогресс этапов", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(if (progress.firstWordsPassed) "1. Первые слова — пройдено" else if (currentMode == TrainingMode.FIRST_WORDS) "1. Первые слова — текущий этап" else "1. Первые слова — доступно")
            Text(if (progress.lastWordsUnlocked) {
                if (progress.lastWordsPassed) "2. Последние слова — пройдено" else if (currentMode == TrainingMode.LAST_WORDS) "2. Последние слова — текущий этап" else "2. Последние слова — открыто"
            } else "2. Последние слова — пока заблокировано")
            Text(if (progress.noHintsUnlocked) {
                if (progress.finalPassed) "3. Без подсказок — экзамен сдан" else if (currentMode == TrainingMode.NO_HINTS) "3. Без подсказок — финальный этап открыт" else "3. Без подсказок — открыто"
            } else "3. Без подсказок — пока заблокировано")
            Text("Лучший результат: ${progress.bestScore}%")
            if (progress.history.isNotEmpty()) Text("История попыток: ${progress.history.joinToString("%, ", postfix = "%")}")
            comparison?.let { Text("Последний результат: ${it.scorePercent}%") }
        }
    }
}

@Composable
private fun AttemptsCard(attempts: List<AttemptEntity>) {
    val formatter = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("История попыток", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            attempts.forEach { attempt ->
                Text("${formatter.format(Date(attempt.createdAt))} · ${attempt.mode} · ${attempt.scorePercent}%")
                if (attempt.recognizedPreview.isNotBlank()) {
                    Text(attempt.recognizedPreview, style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun HintCard(mode: TrainingMode, hints: List<String>) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when (mode) {
                    TrainingMode.FIRST_WORDS -> "Подсказка: первые слова"
                    TrainingMode.LAST_WORDS -> "Подсказка: последние слова"
                    TrainingMode.NO_HINTS -> "Подсказки отключены"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            hints.forEach { Text(it) }
        }
    }
}

private fun buildHints(poemText: String, mode: TrainingMode): List<String> {
    if (mode == TrainingMode.NO_HINTS) return emptyList()
    return poemText.lines().map { it.trim() }.filter { it.isNotBlank() }.map { line ->
        val words = line.split("\\s+".toRegex()).filter { it.isNotBlank() }
        when (mode) {
            TrainingMode.FIRST_WORDS -> words.firstOrNull().orEmpty()
            TrainingMode.LAST_WORDS -> words.lastOrNull().orEmpty()
            TrainingMode.NO_HINTS -> ""
        }
    }
}

private fun startLineRecognition(launcher: (Intent) -> Unit, onError: (String) -> Unit) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Продиктуйте одну строку")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
    }
    try {
        launcher(intent)
    } catch (_: Exception) {
        onError("На устройстве нет приложения для распознавания строки")
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun DiffCard(comparison: ComparisonResult, live: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (live) "Живая подсветка различий" else "Подсветка различий", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Зелёный — совпало, красный — отличается или пропущено, жёлтый — лишние слова.")
            Text(buildAnnotatedString {
                comparison.diffTokens.forEach { token ->
                    val bg = when (token.kind) {
                        DiffKind.MATCH -> Color(0xFFDDF5E3)
                        DiffKind.MISMATCH, DiffKind.MISSING -> Color(0xFFFFDAD6)
                        DiffKind.EXTRA -> Color(0xFFFFF0C2)
                    }
                    withStyle(SpanStyle(background = bg, color = Color.Black)) {
                        append((token.display.ifBlank { "∅" }) + " ")
                    }
                }
            })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnalysisCard(comparison: ComparisonResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Результат анализа", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Совпадение: ${comparison.scorePercent}%")
            Text("Совпавших слов: ${comparison.matchedCount} из ${comparison.expectedCount}")
            HorizontalDivider()
            if (comparison.lineSummaries.isNotEmpty()) {
                Text("По строкам:", fontWeight = FontWeight.Medium)
                comparison.lineSummaries.forEach { Text("• $it") }
            }
            if (comparison.mismatches.isNotEmpty()) {
                Text("Подозрительные места:", fontWeight = FontWeight.Medium)
                comparison.mismatches.forEach { Text("• $it") }
            }
            if (comparison.missingWords.isNotEmpty()) {
                Text("Возможно пропущено:", fontWeight = FontWeight.Medium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    comparison.missingWords.forEach { word -> AssistChip(onClick = {}, label = { Text(word) }) }
                }
            }
            if (comparison.extraWords.isNotEmpty()) {
                Text("Лишние или распознаны сверх текста:", fontWeight = FontWeight.Medium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    comparison.extraWords.forEach { word -> AssistChip(onClick = {}, label = { Text(word) }) }
                }
            }
        }
    }
}

enum class DiffKind { MATCH, MISMATCH, MISSING, EXTRA }
data class DiffToken(val display: String, val kind: DiffKind)
data class ComparisonResult(
    val expectedCount: Int,
    val matchedCount: Int,
    val scorePercent: Int,
    val mismatches: List<String>,
    val missingWords: List<String>,
    val extraWords: List<String>,
    val lineSummaries: List<String>,
    val diffTokens: List<DiffToken>,
    val alignedWords: List<AlignedWord>
)

data class AlignedWord(val expected: String?, val actual: String?)

object PoemComparator {
    fun compare(originalText: String, spokenText: String): ComparisonResult {
        val expected = tokenize(originalText)
        val actual = tokenize(spokenText)
        val alignment = align(expected, actual)
        val mismatches = mutableListOf<String>()
        val missingWords = mutableListOf<String>()
        val extraWords = mutableListOf<String>()
        var matchedCount = 0
        alignment.forEachIndexed { index, pair ->
            when {
                pair.expected == pair.actual && pair.expected != null -> matchedCount += 1
                pair.expected == null && pair.actual != null -> extraWords += pair.actual
                pair.expected != null && pair.actual == null -> missingWords += pair.expected
                pair.expected != null && pair.actual != null -> mismatches += "${index + 1}. ожидалось '${pair.expected}', распознано '${pair.actual}'"
            }
        }
        val lineSummaries = buildLineSummaries(originalText.lines().filter { it.isNotBlank() }, spokenText.lines().filter { it.isNotBlank() })
        val diffTokens = alignment.map {
            when {
                it.expected == it.actual && it.expected != null -> DiffToken(it.expected, DiffKind.MATCH)
                it.expected == null && it.actual != null -> DiffToken(it.actual, DiffKind.EXTRA)
                it.expected != null && it.actual == null -> DiffToken(it.expected, DiffKind.MISSING)
                else -> DiffToken("${it.expected}/${it.actual}", DiffKind.MISMATCH)
            }
        }
        val scorePercent = if (expected.isEmpty()) 100 else (matchedCount * 100.0 / expected.size).toInt()
        return ComparisonResult(
            expectedCount = expected.size,
            matchedCount = matchedCount,
            scorePercent = scorePercent,
            mismatches = mismatches.take(12),
            missingWords = missingWords.distinct().take(20),
            extraWords = extraWords.distinct().take(20),
            lineSummaries = lineSummaries.take(8),
            diffTokens = diffTokens.take(180),
            alignedWords = alignment.take(180)
        )

    }

    private fun buildLineSummaries(expectedLines: List<String>, spokenLines: List<String>): List<String> {
        val summaries = mutableListOf<String>()
        val count = max(expectedLines.size, spokenLines.size)
        for (i in 0 until count) {
            val expected = expectedLines.getOrNull(i)
            val actual = spokenLines.getOrNull(i)
            when {
                expected == null && actual != null -> summaries += "Строка ${i + 1}: распознана лишняя строка «${actual.take(40)}»"
                expected != null && actual == null -> summaries += "Строка ${i + 1}: возможно пропущена «${expected.take(40)}»"
                expected != null && actual != null -> {
                    val prefix = commonPrefixLength(tokenize(expected), tokenize(actual))
                    summaries += if (prefix == 0) "Строка ${i + 1}: начало строки сильно отличается" else "Строка ${i + 1}: совпадение в начале примерно $prefix слов"
                }
            }
        }
        return summaries
    }

    private fun commonPrefixLength(a: List<String>, b: List<String>): Int {
        val size = minOf(a.size, b.size)
        for (i in 0 until size) if (a[i] != b[i]) return i
        return size
    }

    /** Tokenized word count per poem line, in order, skipping lines that tokenize to nothing. */
    fun lineWordCounts(text: String): List<Int> {
        return text.lines().map { tokenize(it).size }.filter { it > 0 }
    }

    private fun tokenize(text: String): List<String> {
        val normalized = Normalizer.normalize(text.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .replace("ё", "е")
            .replace("[^a-zа-я0-9\\n ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
        return if (normalized.isBlank()) emptyList() else normalized.split(' ')
    }

    private fun align(expected: List<String>, actual: List<String>): List<AlignedWord> {
        val dp = Array(expected.size + 1) { IntArray(actual.size + 1) }
        for (i in expected.size downTo 0) {
            for (j in actual.size downTo 0) {
                if (i == expected.size) dp[i][j] = actual.size - j
                else if (j == actual.size) dp[i][j] = expected.size - i
                else {
                    val substitution = if (expected[i] == actual[j]) 0 else 1
                    dp[i][j] = minOf(dp[i + 1][j + 1] + substitution, dp[i + 1][j] + 1, dp[i][j + 1] + 1)
                }
            }
        }
        val aligned = mutableListOf<AlignedWord>()
        var i = 0
        var j = 0
        while (i < expected.size || j < actual.size) {
            when {
                i == expected.size -> { aligned += AlignedWord(null, actual[j]); j++ }
                j == actual.size -> { aligned += AlignedWord(expected[i], null); i++ }
                dp[i][j] == dp[i + 1][j + 1] + if (expected[i] == actual[j]) 0 else 1 -> {
                    aligned += AlignedWord(expected[i], actual[j]); i++; j++
                }
                dp[i][j] == dp[i + 1][j] + 1 -> { aligned += AlignedWord(expected[i], null); i++ }
                else -> { aligned += AlignedWord(null, actual[j]); j++ }
            }
        }
        return aligned
    }
}
