package com.example.poemchecker.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.poemchecker.domain.model.SessionKind
import com.example.poemchecker.ui.editor.EditorScreen
import com.example.poemchecker.ui.editor.EditorViewModel
import com.example.poemchecker.ui.exam.ExamScreen
import com.example.poemchecker.ui.recitation.RecitationViewModel
import com.example.poemchecker.ui.training.TrainingScreen

private enum class Screen { EDITOR, TRAINING, EXAM }

@Composable
fun PoemCheckerApp(container: AppContainer) {
    val editor: EditorViewModel = viewModel(factory = container.editorFactory)
    val recitation: RecitationViewModel = viewModel(factory = container.recitationFactory)
    val editorState by editor.state.collectAsStateWithLifecycle()
    val session by recitation.state.collectAsStateWithLifecycle()
    val poems by editor.savedPoems.collectAsStateWithLifecycle()
    val attempts by editor.attempts.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var screen by rememberSaveable { mutableStateOf(Screen.EDITOR) }
    var pendingLine by remember { mutableStateOf(false) }
    var pendingSession by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(session.result) { session.result?.let(editor::acceptResult) }
    LaunchedEffect(session.session) {
        // A route alone cannot restore a microphone session after process death.
        if (session.session == null) screen = Screen.EDITOR
    }
    DisposableEffect(lifecycleOwner, recitation) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                pendingSession = null
                recitation.cancelListening()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            recitation.cancelListening()
        }
    }

    val lineLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
        editor.appendRecognizedLine(text)
    }
    val dictateLine: () -> Unit = {
        editor.setDictatingLine(true)
        try {
            lineLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Продиктуйте одну строку")
            })
        } catch (_: Exception) {
            editor.showError("На устройстве нет приложения для распознавания строки")
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val line = pendingLine
        val sessionId = pendingSession
        pendingLine = false
        pendingSession = null
        if (line) {
            if (granted) dictateLine() else editor.showError("Нужен доступ к микрофону")
        } else if (sessionId != null && recitation.state.value.session?.id == sessionId && screen != Screen.EDITOR) {
            if (granted) recitation.startListening() else recitation.showError("Нужен доступ к микрофону")
        }
    }

    fun hasMicrophonePermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    val beginListening: () -> Unit = {
        when {
            session.isListening || session.canRetrySave -> recitation.finish()
            hasMicrophonePermission() -> recitation.startListening()
            else -> {
                pendingLine = false
                pendingSession = recitation.state.value.session?.id
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
    val leaveSession: () -> Unit = {
        recitation.state.value.result?.let(editor::acceptResult)
        if (recitation.close()) {
            pendingSession = null
            screen = Screen.EDITOR
        }
    }
    val openSession: (SessionKind) -> Unit = { kind ->
        val state = editor.state.value
        if (!state.isSaving && recitation.open(state.currentPoemId, state.poemText, state.trainingMode, kind, state.trainingProgress)) {
            screen = if (kind == SessionKind.EXAM) Screen.EXAM else Screen.TRAINING
            if (kind == SessionKind.TRAINING) {
                if (hasMicrophonePermission()) recitation.startListening() else {
                    pendingSession = recitation.state.value.session?.id
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        } else {
            editor.showError("Сначала задайте текст стиха")
        }
    }
    BackHandler(enabled = screen != Screen.EDITOR) { leaveSession() }

    when (screen) {
        Screen.TRAINING -> TrainingScreen(session, leaveSession, beginListening)
        Screen.EXAM -> ExamScreen(session, attempts, leaveSession, beginListening)
        Screen.EDITOR -> EditorScreen(
            editorState = editorState,
            savedPoems = poems,
            attempts = attempts,
            onTextChanged = editor::updatePoem,
            onClear = editor::clearPoem,
            onSave = editor::saveCurrentPoem,
            onDictateLine = {
                if (hasMicrophonePermission()) dictateLine() else {
                    pendingLine = true
                    pendingSession = null
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onTraining = { openSession(SessionKind.TRAINING) },
            onExam = { openSession(SessionKind.EXAM) },
            onModeSelected = editor::setTrainingMode,
            onLoad = editor::loadPoem,
            onDelete = editor::deletePoem
        )
    }
}
