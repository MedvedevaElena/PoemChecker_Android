package com.example.poemchecker.domain.repository

import com.example.poemchecker.domain.model.Attempt
import com.example.poemchecker.domain.model.Poem
import com.example.poemchecker.domain.model.TrainingProgress
import kotlinx.coroutines.flow.Flow

interface PoemRepository {
    fun observePoems(): Flow<List<Poem>>
    fun observeAttempts(poemId: Long): Flow<List<Attempt>>
    suspend fun getPoem(poemId: Long): Poem?
    suspend fun savePoem(poem: Poem): Long
    suspend fun deletePoem(poemId: Long)
    suspend fun recordAttempt(attempt: Attempt, progress: TrainingProgress)
}
