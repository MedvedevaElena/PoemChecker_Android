package com.example.poemchecker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

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

