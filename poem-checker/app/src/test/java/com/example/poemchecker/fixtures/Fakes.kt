package com.example.poemchecker.fixtures

import com.example.poemchecker.domain.model.*
import com.example.poemchecker.domain.repository.PoemRepository
import com.example.poemchecker.speech.SpeechRecognitionClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakePoemRepository : PoemRepository {
    val poems = MutableStateFlow<List<Poem>>(emptyList())
    val attempts = MutableStateFlow<List<Attempt>>(emptyList())
    var failNextAttempt = false
    override fun observePoems() = poems
    override fun observeAttempts(poemId: Long) = attempts.map { rows -> rows.filter { it.poemId == poemId } }
    override suspend fun getPoem(poemId: Long) = poems.value.find { it.id == poemId }
    override suspend fun savePoem(poem: Poem): Long {
        val id = poem.id.takeIf { it != 0L } ?: ((poems.value.maxOfOrNull { it.id } ?: 0) + 1)
        poems.value = poems.value.filterNot { it.id == id } + poem.copy(id = id)
        return id
    }
    override suspend fun deletePoem(poemId: Long) {
        poems.value = poems.value.filterNot { it.id == poemId }
        attempts.value = attempts.value.filterNot { it.poemId == poemId }
    }
    override suspend fun recordAttempt(attempt: Attempt, progress: TrainingProgress) {
        if (failNextAttempt) {
            failNextAttempt = false
            error("Storage failure")
        }
        attempts.value = attempts.value + attempt
        poems.value = poems.value.map { if (it.id == attempt.poemId) it.copy(progress = progress) else it }
    }
}

class FakeSpeechClient : SpeechRecognitionClient {
    var text = ""
    var cancelled = false
    var onText: (String) -> Unit = {}
    var onError: (String) -> Unit = {}
    override fun start(onText: (String) -> Unit, onError: (String) -> Unit) {
        text = ""
        cancelled = false
        this.onText = onText
        this.onError = onError
    }
    fun emit(text: String) { this.text = text; onText(text) }
    override fun stop(): String { cancelled = true; return text }
    override fun cancel() { cancelled = true }
}
