package com.example.poemchecker.ui.training

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.poemchecker.domain.model.TrainingMode
import com.example.poemchecker.ui.components.AnalysisCard
import com.example.poemchecker.ui.recitation.RecitationUiState

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TrainingScreen(
    state: RecitationUiState,
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
                IconButton(onClick = onBack, enabled = !state.isSaving) {
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
                            TrainingMode.FIRST_AND_LAST_WORDS -> "Режим: первые и последние слова"
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
                    enabled = !state.isSaving,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(if (state.isSaving) "Сохраняю…" else if (state.canRetrySave) "Повторить сохранение" else if (state.isListening) "Остановить" else "Начать говорить")
                }
                Button(
                    onClick = onBack,
                    enabled = !state.isSaving,
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
            state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
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

