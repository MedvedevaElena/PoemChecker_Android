package com.example.poemchecker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.poemchecker.domain.comparison.ComparisonResult
import com.example.poemchecker.domain.comparison.DiffKind
import com.example.poemchecker.domain.model.Attempt
import com.example.poemchecker.domain.model.Poem
import com.example.poemchecker.domain.model.TrainingMode
import com.example.poemchecker.domain.model.TrainingProgress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun PoemCard(poem: Poem, onLoad: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onLoad() }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(poem.title, fontWeight = FontWeight.SemiBold)
                Text(poem.text.take(120))
                Text(
                    when {
                        poem.progress.finalPassed -> "Статус: стих выучен"
                        poem.progress.lastWordsPassed -> "Статус: пройдены последние слова"
                        poem.progress.firstWordsPassed -> "Статус: открыты последние слова"
                        else -> "Статус: начало обучения"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                Text("Лучший результат: ${poem.progress.bestScore}%", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Удалить стих") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TrainingModeCard(selectedMode: TrainingMode, onModeSelected: (TrainingMode) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Режимы тренировки", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TrainingMode.values().forEach { mode ->
                    val label = when (mode) {
                        TrainingMode.FIRST_WORDS -> "Первые"
                        TrainingMode.LAST_WORDS -> "Последние"
                        TrainingMode.FIRST_AND_LAST_WORDS -> "Первые+последние"
                        TrainingMode.NO_HINTS -> "Без подсказки"
                    }
                    AssistChip(onClick = { onModeSelected(mode) }, label = { Text(label) })
                }
            }
            Text(
                when (selectedMode) {
                    TrainingMode.FIRST_WORDS -> "Сейчас видны только первые слова строк."
                    TrainingMode.LAST_WORDS -> "Сейчас видны только последние слова строк."
                    TrainingMode.FIRST_AND_LAST_WORDS -> "Сейчас видны первые и последние слова строк."
                    TrainingMode.NO_HINTS -> "Подсказки отключены. Можно тренироваться или сдавать экзамен."
                }
            )
        }
    }
}

@Composable
internal fun TrainingProgressCard(progress: TrainingProgress, comparison: ComparisonResult?, currentMode: TrainingMode) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Прогресс этапов", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(if (progress.firstWordsPassed) "1. Первые слова — пройдено" else if (currentMode == TrainingMode.FIRST_WORDS) "1. Первые слова — текущий этап" else "1. Первые слова — доступно")
            Text(if (progress.lastWordsPassed) "2. Последние слова — пройдено" else if (currentMode == TrainingMode.LAST_WORDS) "2. Последние слова — текущий этап" else "2. Последние слова — доступно")
            Text(if (progress.finalPassed) "3. Без подсказок — пройдено" else if (currentMode == TrainingMode.NO_HINTS) "3. Без подсказок — текущий этап" else "3. Без подсказок — доступно")
            Text("Лучший результат: ${progress.bestScore}%")
            if (progress.history.isNotEmpty()) Text("История попыток: ${progress.history.joinToString("%, ", postfix = "%")}")
            comparison?.let { Text("Последний результат: ${it.scorePercent}%") }
        }
    }
}

@Composable
internal fun AttemptsCard(attempts: List<Attempt>) {
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
internal fun HintCard(mode: TrainingMode, hints: List<String>) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when (mode) {
                    TrainingMode.FIRST_WORDS -> "Подсказка: первые слова"
                    TrainingMode.LAST_WORDS -> "Подсказка: последние слова"
                    TrainingMode.FIRST_AND_LAST_WORDS -> "Подсказка: первые и последние слова"
                    TrainingMode.NO_HINTS -> "Подсказки отключены"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            hints.forEach { Text(it) }
        }
    }
}

@Composable
internal fun InfoCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
internal fun DiffCard(comparison: ComparisonResult, live: Boolean) {
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
internal fun AnalysisCard(comparison: ComparisonResult) {
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

