package com.example.poemchecker.ui.exam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.poemchecker.domain.model.Attempt
import com.example.poemchecker.ui.components.AttemptsCard
import com.example.poemchecker.ui.recitation.RecitationUiState

@Composable
internal fun ExamScreen(
    state: RecitationUiState,
    attempts: List<Attempt>,
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
                    IconButton(onClick = onBack, enabled = !state.isSaving) {
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
                        Button(onClick = onStart, enabled = !state.isSaving, shape = RoundedCornerShape(16.dp)) {
                            Text(if (state.isSaving) "Сохраняю…" else if (state.canRetrySave) "Повторить сохранение" else if (state.isListening) "Остановить экзамен" else "Начать экзамен")
                        }
                    }
                }
            }
            state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            state.result?.comparison?.let { comparison ->
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
            if (state.result != null && attempts.isNotEmpty()) {
                item { AttemptsCard(attempts) }
            }
        }
    }
}

