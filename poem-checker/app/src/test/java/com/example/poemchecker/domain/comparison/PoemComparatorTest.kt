package com.example.poemchecker.domain.comparison

import org.junit.Assert.*
import org.junit.Test

class PoemComparatorTest {
    @Test fun exactMultilineRecitationMatches() {
        val result = PoemComparator.compare("Буря мглою\nНебо кроет", "буря мглою небо кроет")
        assertEquals(100, result.scorePercent)
        assertEquals(4, result.matchedCount)
        assertTrue(result.missingWords.isEmpty())
    }

    @Test fun substitutionAndIntegerTruncation() {
        val result = PoemComparator.compare("раз два три", "раз пять три")
        assertEquals(66, result.scorePercent)
        assertEquals(1, result.mismatches.size)
    }

    @Test fun omittedWordIsReported() {
        val result = PoemComparator.compare("раз два три", "раз три")
        assertEquals(listOf("два"), result.missingWords)
        assertEquals(66, result.scorePercent)
    }

    @Test fun extraWordsDoNotDirectlyPenalizeScore() {
        val result = PoemComparator.compare("раз два", "раз пять два")
        assertEquals(100, result.scorePercent)
        assertEquals(listOf("пять"), result.extraWords)
    }

    @Test fun repeatedWordsAreCountedIndividually() {
        val result = PoemComparator.compare("да да да", "да да")
        assertEquals(3, result.expectedCount)
        assertEquals(2, result.matchedCount)
        assertEquals(66, result.scorePercent)
    }

    @Test fun changingWordOrderIsNotAnExactMatch() {
        assertTrue(PoemComparator.compare("один два три", "три два один").scorePercent < 100)
    }

    @Test fun currentRussianNormalizationIsPreserved() {
        val result = PoemComparator.compare("ЁЖ, йод! 42", "еж иод 42")
        assertEquals(100, result.scorePercent)
    }

    @Test fun emptyInputKeepsComparatorConvention() {
        assertEquals(100, PoemComparator.compare("", "").scorePercent)
        assertEquals(0, PoemComparator.compare("текст", "").scorePercent)
    }

    @Test fun punctuationOnlyLinesDoNotCreateHintLines() {
        assertEquals(listOf(2, 1), PoemComparator.lineWordCounts("один два\n---\n\nтри"))
    }

    @Test fun longPoemScoreIncludesTailBeyondDisplayLimit() {
        val words = (1..250).map { "слово$it" }
        val result = PoemComparator.compare(words.joinToString(" "), words.take(200).joinToString(" "))
        assertEquals(250, result.expectedCount)
        assertEquals(80, result.scorePercent)
        assertFalse(result.isComplete)
        assertEquals(250, result.alignedWords.size)
        assertTrue(PoemComparator.compare(words.joinToString(" "), words.joinToString(" ")).isComplete)
        assertFalse(PoemComparator.compare("", "").isComplete)
    }
}
