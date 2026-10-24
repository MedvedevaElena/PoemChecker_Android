package com.example.poemchecker.data.repository

import androidx.room.withTransaction
import com.example.poemchecker.data.local.*
import com.example.poemchecker.domain.model.*
import com.example.poemchecker.domain.repository.PoemRepository
import kotlinx.coroutines.flow.map

class RoomPoemRepository(private val database: PoemDatabase) : PoemRepository {
    private val poems = database.poemDao()
    private val attempts = database.attemptDao()

    override fun observePoems() = poems.observeAll().map { rows -> rows.map { it.toDomain() } }
    override fun observeAttempts(poemId: Long) = attempts.observeForPoem(poemId).map { rows ->
        rows.map { Attempt(it.id, it.poemId, it.mode, it.scorePercent, it.createdAt, it.recognizedPreview) }
    }
    override suspend fun getPoem(poemId: Long) = poems.getById(poemId)?.toDomain()

    override suspend fun savePoem(poem: Poem): Long {
        val text = poem.text.trim()
        require(text.isNotBlank())
        val progress = poem.progress
        return poems.insert(SavedPoem(
            id = poem.id,
            title = text.lineSequence().firstOrNull().orEmpty().take(40).ifBlank { "Без названия" },
            text = text,
            firstWordsPassed = progress.firstWordsPassed,
            lastWordsPassed = progress.lastWordsPassed,
            noHintsUnlocked = true,
            finalPassed = progress.finalPassed,
            scoreHistory = progress.history.takeLast(20).joinToString(","),
            bestScore = progress.bestScore
        ))
    }

    override suspend fun deletePoem(poemId: Long) = database.withTransaction {
        attempts.deleteForPoem(poemId)
        poems.deleteById(poemId)
    }

    override suspend fun recordAttempt(attempt: Attempt, progress: TrainingProgress) {
        database.withTransaction {
            val old = poems.getById(attempt.poemId) ?: return@withTransaction
            // Update only progress; the immutable recitation snapshot must not overwrite later edits.
            poems.insert(old.copy(
                firstWordsPassed = progress.firstWordsPassed,
                lastWordsPassed = progress.lastWordsPassed,
                noHintsUnlocked = true,
                finalPassed = progress.finalPassed,
                scoreHistory = progress.history.takeLast(20).joinToString(","),
                bestScore = progress.bestScore
            ))
            attempts.insert(AttemptEntity(
                poemId = attempt.poemId,
                mode = attempt.mode,
                scorePercent = attempt.scorePercent,
                createdAt = attempt.createdAt,
                recognizedPreview = attempt.recognizedPreview.take(120)
            ))
        }
    }
}

private fun SavedPoem.toDomain() = Poem(
    id = id, title = title, text = text,
    progress = TrainingProgress(
        firstWordsPassed = firstWordsPassed,
        lastWordsPassed = lastWordsPassed,
        finalPassed = finalPassed,
        history = scoreHistory.split(',').mapNotNull { it.toIntOrNull() },
        bestScore = bestScore
    )
)
