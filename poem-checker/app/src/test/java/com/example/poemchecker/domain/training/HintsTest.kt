package com.example.poemchecker.domain.training

import com.example.poemchecker.domain.model.TrainingMode
import org.junit.Assert.*
import org.junit.Test

class HintsTest {
    private val poem = "Первое последнее\n\nОдин"
    @Test fun firstWordsRespectLineBreaks() {
        assertEquals(listOf("Первое", "Один"), buildHints(poem, TrainingMode.FIRST_WORDS))
    }
    @Test fun lastWordsRespectLineBreaks() {
        assertEquals(listOf("последнее", "Один"), buildHints(poem, TrainingMode.LAST_WORDS))
    }
    @Test fun combinedHintsDoNotDuplicateSingleWordLines() {
        assertEquals(listOf("Первое … последнее", "Один"), buildHints(poem, TrainingMode.FIRST_AND_LAST_WORDS))
    }
    @Test fun noHintsReturnsNoReferenceWords() {
        assertTrue(buildHints(poem, TrainingMode.NO_HINTS).isEmpty())
    }
}
