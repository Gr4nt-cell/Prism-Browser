package com.gr4nt.browser.browser

import android.text.Html
import kotlin.math.ln
import kotlin.math.max

/**
 * On-device extractive summary for "Pinch to Summarize": scores each sentence of the article by
 * how many of the article's key words it contains (TF-IDF-like, stop words removed), favours
 * sentences near the top, and returns the best few in their original order. No network, no AI service.
 */
object Summarizer {
    class Result(val points: List<String>, val minutes: Int, val words: Int)

    private val STOP = setOf(
        "the", "a", "an", "and", "or", "but", "if", "then", "of", "to", "in", "on", "at", "for", "with", "by", "from", "as",
        "is", "are", "was", "were", "be", "been", "being", "it", "its", "this", "that", "these", "those", "there", "their",
        "they", "them", "he", "she", "his", "her", "we", "our", "you", "your", "i", "me", "my", "not", "no", "so", "do", "does",
        "did", "have", "has", "had", "will", "would", "can", "could", "should", "may", "might", "also", "than", "more", "most",
        "about", "into", "over", "after", "before", "which", "who", "whom", "what", "when", "where", "why", "how", "all", "any",
        "some", "such", "just", "only", "very", "said", "says", "one", "two", "new", "like", "out", "up", "us", "while"
    )

    fun summarize(html: String, maxPoints: Int = 5): Result? {
        @Suppress("DEPRECATION")
        val text = Html.fromHtml(html.replace(Regex("(?i)</(p|li|h[1-6]|div|br)>"), ".\n"), Html.FROM_HTML_MODE_LEGACY).toString()
            .replace('￼', ' ').replace(Regex("[ \\t\\u00A0]+"), " ")
        val sentences = Regex("""(?<=[.!?])\s+(?=[A-Z0-9"“‘(])""").split(text.replace('\n', ' '))
            .map { it.trim().trim('.').trim() }
            .filter { it.length in 50..320 && it.count { c -> c == ' ' } >= 7 }
            .distinct()
        val allWords = Regex("[\\p{L}\\p{N}']+").findAll(text.lowercase()).map { it.value }.toList()
        if (sentences.size < 3 || allWords.size < 150) return null

        val freq = HashMap<String, Int>()
        for (w in allWords) if (w.length > 2 && w !in STOP) freq[w] = (freq[w] ?: 0) + 1
        val maxF = freq.values.maxOrNull()?.toDouble() ?: return null
        val scored = sentences.mapIndexed { i, s ->
            val ws = Regex("[\\p{L}\\p{N}']+").findAll(s.lowercase()).map { it.value }.filter { it.length > 2 && it !in STOP }.toList()
            val score = ws.sumOf { (freq[it] ?: 0) / maxF } / max(1.0, ln(ws.size + 1.0) * 2.2)
            val position = 1.0 + 0.6 * (1.0 - i.toDouble() / sentences.size)    // lead sentences matter more
            Triple(i, s, score * position)
        }
        val picked = scored.sortedByDescending { it.third }.take(maxPoints).sortedBy { it.first }.map { tidy(it.second) }
        val words = allWords.size
        return Result(picked, max(1, words / 230), words)
    }

    private fun tidy(s: String): String {
        var t = s.replace(Regex("\\s+"), " ").trim()
        if (!t.endsWith(".") && !t.endsWith("!") && !t.endsWith("?")) t += "."
        return t.replaceFirstChar { it.uppercase() }
    }
}
