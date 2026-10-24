package com.example.poemchecker.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

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

