package com.example.poemchecker.ui.training

import com.example.poemchecker.domain.model.TrainingMode
import com.example.poemchecker.domain.comparison.*

data class LiveWordUi(
    val expected: String?,
    val actual: String?,
    val state: WordState,
    val isHint: Boolean = false
)

enum class WordState {
    MATCH,
    WRONG,
    MISSING,
    EXTRA
}

private fun toLiveWordUi(pair: AlignedWord, isHint: Boolean): LiveWordUi {
    return when {
        pair.expected == pair.actual && pair.expected != null ->
            LiveWordUi(expected = pair.expected, actual = pair.actual, state = WordState.MATCH, isHint = isHint)

        pair.expected != null && pair.actual != null ->
            LiveWordUi(expected = pair.expected, actual = pair.actual, state = WordState.WRONG, isHint = isHint)

        pair.expected != null && pair.actual == null ->
            LiveWordUi(expected = pair.expected, actual = null, state = WordState.MISSING, isHint = isHint)

        else ->
            LiveWordUi(expected = null, actual = pair.actual, state = WordState.EXTRA)
    }
}

/**
 * Groups the live comparison by poem line and marks, within each line, the word that acts as the
 * hint for the current mode (first word / last word) so it can be rendered as its own leaf that's
 * always visible — blue until it's actually said, green once matched — merged into the same flow
 * as the ordinary recognized-word leaves instead of a separate hint block.
 */
internal fun buildLiveLines(
    comparison: ComparisonResult,
    poemText: String,
    mode: TrainingMode
): List<List<LiveWordUi>> {
    val lineWordCounts = PoemComparator.lineWordCounts(poemText)
    if (lineWordCounts.isEmpty()) {
        return listOf(comparison.alignedWords.map { toLiveWordUi(it, isHint = false) })
    }

    val lineStarts = lineWordCounts.runningFold(0) { acc, count -> acc + count }
    val firstWordIndices = lineStarts.dropLast(1).toSet()
    val lastWordIndices = lineStarts.drop(1).map { it - 1 }.toSet()
    val hintIndices: Set<Int> = when (mode) {
        TrainingMode.FIRST_WORDS -> firstWordIndices
        TrainingMode.LAST_WORDS -> lastWordIndices
        TrainingMode.FIRST_AND_LAST_WORDS -> firstWordIndices + lastWordIndices
        TrainingMode.NO_HINTS -> emptySet()
    }

    val lines = MutableList(lineWordCounts.size) { mutableListOf<LiveWordUi>() }
    var lineIndex = 0
    var consumedInLine = 0
    var globalExpectedIndex = 0
    comparison.alignedWords.forEach { pair ->
        val bucketIndex = lineIndex.coerceAtMost(lines.size - 1)
        val isHint = pair.expected != null && globalExpectedIndex in hintIndices
        lines[bucketIndex].add(toLiveWordUi(pair, isHint))
        if (pair.expected != null) {
            globalExpectedIndex++
            consumedInLine++
            if (consumedInLine >= lineWordCounts[bucketIndex] && lineIndex < lines.size - 1) {
                lineIndex++
                consumedInLine = 0
            }
        }
    }
    return lines
}

