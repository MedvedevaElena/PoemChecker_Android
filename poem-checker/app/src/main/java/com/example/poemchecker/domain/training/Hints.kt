package com.example.poemchecker.domain.training

import com.example.poemchecker.domain.model.TrainingMode

fun buildHints(poemText: String, mode: TrainingMode): List<String> {
    if (mode == TrainingMode.NO_HINTS) return emptyList()
    return poemText.lines().map { it.trim() }.filter { it.isNotBlank() }.map { line ->
        val words = line.split("\\s+".toRegex()).filter { it.isNotBlank() }
        when (mode) {
            TrainingMode.FIRST_WORDS -> words.firstOrNull().orEmpty()
            TrainingMode.LAST_WORDS -> words.lastOrNull().orEmpty()
            TrainingMode.FIRST_AND_LAST_WORDS -> {
                val first = words.firstOrNull().orEmpty()
                val last = words.lastOrNull().orEmpty()
                if (words.size > 1) "$first … $last" else first
            }
            TrainingMode.NO_HINTS -> ""
        }
    }
}

