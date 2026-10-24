package com.example.poemchecker.domain.model

enum class TrainingMode {
    FIRST_WORDS,
    LAST_WORDS,
    FIRST_AND_LAST_WORDS,
    NO_HINTS
}

data class TrainingProgress(
    val firstWordsPassed: Boolean = false,
    val lastWordsPassed: Boolean = false,
    val finalPassed: Boolean = false,
    val history: List<Int> = emptyList(),
    val bestScore: Int = 0
)

data class Poem(
    val id: Long = 0,
    val title: String = "",
    val text: String,
    val progress: TrainingProgress = TrainingProgress()
)

data class Attempt(
    val id: Long = 0,
    val poemId: Long,
    val mode: String,
    val scorePercent: Int,
    val createdAt: Long,
    val recognizedPreview: String
)

enum class SessionKind { TRAINING, EXAM }

data class SessionSnapshot(
    val id: Long,
    val poemId: Long?,
    val referenceText: String,
    val mode: TrainingMode,
    val kind: SessionKind
)
