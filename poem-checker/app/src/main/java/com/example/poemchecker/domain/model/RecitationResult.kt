package com.example.poemchecker.domain.model

import com.example.poemchecker.domain.comparison.ComparisonResult
import com.example.poemchecker.domain.training.ProgressUpdate

data class RecitationResult(
    val session: SessionSnapshot,
    val recognizedText: String,
    val comparison: ComparisonResult,
    val update: ProgressUpdate
)
