package com.example.poemchecker.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.poemchecker.domain.model.Attempt
import com.example.poemchecker.domain.model.Poem
import com.example.poemchecker.domain.model.TrainingMode
import com.example.poemchecker.domain.training.buildHints
import com.example.poemchecker.ui.components.AnalysisCard
import com.example.poemchecker.ui.components.AttemptsCard
import com.example.poemchecker.ui.components.DiffCard
import com.example.poemchecker.ui.components.HintCard
import com.example.poemchecker.ui.components.InfoCard
import com.example.poemchecker.ui.components.PoemCard
import com.example.poemchecker.ui.components.TrainingModeCard
import com.example.poemchecker.ui.components.TrainingProgressCard

@Composable
internal fun EditorScreen(
    editorState: EditorUiState,
    savedPoems: List<Poem>,
    attempts: List<Attempt>,
    onTextChanged: (String) -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    onDictateLine: () -> Unit,
    onTraining: () -> Unit,
    onExam: () -> Unit,
    onModeSelected: (TrainingMode) -> Unit,
    onLoad: (Poem) -> Unit,
    onDelete: (Poem) -> Unit
) {
    val hints = buildHints(editorState.poemText, editorState.trainingMode)

    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            editorState.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            item {
                Text("Poem Checker", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            item {
                Text("Введите стих, выберите подсказки и тренируйтесь читать наизусть.")
            }
            item {
                OutlinedTextField(
                    value = editorState.poemText,
                    onValueChange = onTextChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Текст стиха") },
                    enabled = !editorState.isSaving,
                    minLines = 8,
                    shape = RoundedCornerShape(16.dp)
                )
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onDictateLine,
                        enabled = !editorState.isSaving,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text(if (editorState.isDictatingLine) "Слушаю строку…" else "Надиктовать строку") }

                    Button(
                        onClick = onClear,
                        enabled = !editorState.isSaving,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("Очистить") }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onSave,
                        enabled = !editorState.isSaving,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text(if (editorState.currentPoemId == null) "Сохранить стих" else "Обновить стих") }

                    Button(
                        onClick = onTraining,
                        enabled = !editorState.isSaving,
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
                        onClick = onExam,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !editorState.isSaving && editorState.poemText.isNotBlank()
                    ) { Text("Открыть экран экзамена") }
                }
            }
            if (editorState.hasUnsavedChanges) {
                item {
                    Text("Изменения не сохранены. Чтобы сохранять попытки для этого текста, нажмите «Обновить стих».")
                }
            }
            item {
                TrainingModeCard(editorState.trainingMode, onModeSelected)
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
                    PoemCard(poem = poem, onLoad = { onLoad(poem) }, onDelete = { onDelete(poem) })
                }
            }
            if (attempts.isNotEmpty()) {
                item {
                    AttemptsCard(attempts)
                }
            }
            if (editorState.recognizedText.isNotBlank()) {
                item {
                    InfoCard("Финально распознано") { Text(editorState.recognizedText) }
                }
            }
            editorState.comparison?.let { comparison ->
                item { DiffCard(comparison, live = false) }
                item { AnalysisCard(comparison) }
            }
        }
    }
}

