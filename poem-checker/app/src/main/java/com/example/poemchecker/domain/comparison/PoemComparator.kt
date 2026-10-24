package com.example.poemchecker.domain.comparison

import java.text.Normalizer
import java.util.Locale
import kotlin.math.max

enum class DiffKind { MATCH, MISMATCH, MISSING, EXTRA }
data class DiffToken(val display: String, val kind: DiffKind)
data class ComparisonResult(
    val expectedCount: Int,
    val matchedCount: Int,
    val scorePercent: Int,
    val mismatches: List<String>,
    val missingWords: List<String>,
    val extraWords: List<String>,
    val lineSummaries: List<String>,
    val diffTokens: List<DiffToken>,
    val alignedWords: List<AlignedWord>
) {
    val isComplete: Boolean
        get() = expectedCount > 0 && alignedWords.none { it.expected != null && it.actual == null }
}

data class AlignedWord(val expected: String?, val actual: String?)

object PoemComparator {
    fun compare(originalText: String, spokenText: String): ComparisonResult {
        val expected = tokenize(originalText)
        val actual = tokenize(spokenText)
        val alignment = align(expected, actual)
        val mismatches = mutableListOf<String>()
        val missingWords = mutableListOf<String>()
        val extraWords = mutableListOf<String>()
        var matchedCount = 0
        alignment.forEachIndexed { index, pair ->
            when {
                pair.expected == pair.actual && pair.expected != null -> matchedCount += 1
                pair.expected == null && pair.actual != null -> extraWords += pair.actual
                pair.expected != null && pair.actual == null -> missingWords += pair.expected
                pair.expected != null && pair.actual != null -> mismatches += "${index + 1}. ожидалось '${pair.expected}', распознано '${pair.actual}'"
            }
        }
        val lineSummaries = buildLineSummaries(originalText.lines().filter { it.isNotBlank() }, spokenText.lines().filter { it.isNotBlank() })
        val diffTokens = alignment.map {
            when {
                it.expected == it.actual && it.expected != null -> DiffToken(it.expected, DiffKind.MATCH)
                it.expected == null && it.actual != null -> DiffToken(it.actual, DiffKind.EXTRA)
                it.expected != null && it.actual == null -> DiffToken(it.expected, DiffKind.MISSING)
                else -> DiffToken("${it.expected}/${it.actual}", DiffKind.MISMATCH)
            }
        }
        val scorePercent = if (expected.isEmpty()) 100 else (matchedCount * 100.0 / expected.size).toInt()
        return ComparisonResult(
            expectedCount = expected.size,
            matchedCount = matchedCount,
            scorePercent = scorePercent,
            mismatches = mismatches.take(12),
            missingWords = missingWords.distinct().take(20),
            extraWords = extraWords.distinct().take(20),
            lineSummaries = lineSummaries.take(8),
            diffTokens = diffTokens.take(180),
            alignedWords = alignment
        )

    }

    private fun buildLineSummaries(expectedLines: List<String>, spokenLines: List<String>): List<String> {
        val summaries = mutableListOf<String>()
        val count = max(expectedLines.size, spokenLines.size)
        for (i in 0 until count) {
            val expected = expectedLines.getOrNull(i)
            val actual = spokenLines.getOrNull(i)
            when {
                expected == null && actual != null -> summaries += "Строка ${i + 1}: распознана лишняя строка «${actual.take(40)}»"
                expected != null && actual == null -> summaries += "Строка ${i + 1}: возможно пропущена «${expected.take(40)}»"
                expected != null && actual != null -> {
                    val prefix = commonPrefixLength(tokenize(expected), tokenize(actual))
                    summaries += if (prefix == 0) "Строка ${i + 1}: начало строки сильно отличается" else "Строка ${i + 1}: совпадение в начале примерно $prefix слов"
                }
            }
        }
        return summaries
    }

    private fun commonPrefixLength(a: List<String>, b: List<String>): Int {
        val size = minOf(a.size, b.size)
        for (i in 0 until size) if (a[i] != b[i]) return i
        return size
    }

    /** Tokenized word count per poem line, in order, skipping lines that tokenize to nothing. */
    fun lineWordCounts(text: String): List<Int> {
        return text.lines().map { tokenize(it).size }.filter { it > 0 }
    }

    private fun tokenize(text: String): List<String> {
        val normalized = Normalizer.normalize(text.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .replace("ё", "е")
            .replace("[^a-zа-я0-9\\n ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
        return if (normalized.isBlank()) emptyList() else normalized.split(' ')
    }

    private fun align(expected: List<String>, actual: List<String>): List<AlignedWord> {
        val dp = Array(expected.size + 1) { IntArray(actual.size + 1) }
        for (i in expected.size downTo 0) {
            for (j in actual.size downTo 0) {
                if (i == expected.size) dp[i][j] = actual.size - j
                else if (j == actual.size) dp[i][j] = expected.size - i
                else {
                    val substitution = if (expected[i] == actual[j]) 0 else 1
                    dp[i][j] = minOf(dp[i + 1][j + 1] + substitution, dp[i + 1][j] + 1, dp[i][j + 1] + 1)
                }
            }
        }
        val aligned = mutableListOf<AlignedWord>()
        var i = 0
        var j = 0
        while (i < expected.size || j < actual.size) {
            when {
                i == expected.size -> { aligned += AlignedWord(null, actual[j]); j++ }
                j == actual.size -> { aligned += AlignedWord(expected[i], null); i++ }
                dp[i][j] == dp[i + 1][j + 1] + if (expected[i] == actual[j]) 0 else 1 -> {
                    aligned += AlignedWord(expected[i], actual[j]); i++; j++
                }
                dp[i][j] == dp[i + 1][j] + 1 -> { aligned += AlignedWord(expected[i], null); i++ }
                else -> { aligned += AlignedWord(null, actual[j]); j++ }
            }
        }
        return aligned
    }
}
