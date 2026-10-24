package com.example.poemchecker.ui

import com.example.poemchecker.domain.comparison.PoemComparator
import com.example.poemchecker.domain.model.TrainingMode
import com.example.poemchecker.ui.training.*
import org.junit.Assert.*
import org.junit.Test

class LiveWordsTest {
    @Test fun hintsArePlacedPerTokenizedLine() {
        val poem = "раз два\n---\nтри"
        val lines = buildLiveLines(PoemComparator.compare(poem, ""), poem, TrainingMode.FIRST_AND_LAST_WORDS)
        assertEquals(2, lines.size)
        assertEquals(listOf("раз", "два"), lines[0].filter { it.isHint }.map { it.expected })
        assertEquals(1, lines[1].count { it.isHint })
    }

    @Test fun matchedHintTurnsIntoAMatchAndNoHintsModeMarksNothing() {
        val poem = "раз два"
        val comparison = PoemComparator.compare(poem, "раз")
        val first = buildLiveLines(comparison, poem, TrainingMode.FIRST_WORDS).first().first()
        assertTrue(first.isHint)
        assertEquals(WordState.MATCH, first.state)
        assertFalse(buildLiveLines(comparison, poem, TrainingMode.NO_HINTS).flatten().any { it.isHint })
    }
}
