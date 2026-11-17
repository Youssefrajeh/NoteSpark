package com.group15.notemate.ml

import com.group15.notemate.model.Flashcard
import kotlin.math.ceil

/** Plain text helpers used by [NoteAnalyzer] and the editor. No Android or ML Kit code, so they can be unit tested. */
internal object TextHelpers {

    /** A sentence ends at ". ", "! " or "? ", or at the end of the line. */
    private val SENTENCE_END = Regex("[.!?]\\s+|\\n")
    private val WORD_SPLIT = Regex("\\s+")
    private val BULLET_PREFIX = Regex("^[•\\-*0-9.)]+\\s*")
    private val KEY_VALUE_SPLIT = Regex("\\s*(?::|\\s-\\s|\\s–\\s|\\s—\\s)\\s*")

    /**
     * The sentence of [text] that holds the date found between [start] and [end].
     * The start and end of the sentence are searched for outside the date, so "Nov. 28" is not cut in two.
     */
    fun sentenceAround(text: String, start: Int, end: Int): String {
        val from = SENTENCE_END.findAll(text.substring(0, start)).lastOrNull()?.range?.last?.plus(1) ?: 0
        // From the last character of the date, because "5pm." comes back with its full stop.
        // Keeps the full stop, but not the space or the line break after it.
        val to = SENTENCE_END.find(text, maxOf(start, end - 1))?.range?.first?.plus(1) ?: text.length
        return text.substring(from, to).trim()
    }

    /** Counts words in [text]. */
    fun wordCount(text: String): Int {
        val trimmed = text.trim()
        return if (trimmed.isEmpty()) 0 else trimmed.split(WORD_SPLIT).size
    }

    /** Estimated reading time in minutes at ~200 words per minute (at least 1 minute when non-empty). */
    fun readingMinutes(words: Int): Int = if (words <= 0) 0 else maxOf(1, ceil(words / 200.0).toInt())

    /**
     * Builds study flashcards from [title], [content] and [summary]:
     * 1. Lines formatted like "Term: Definition" or "Term - Definition" become Q/A cards.
     * 2. Remaining bullet points or sentences become fill-in-the-blank / review cards.
     */
    fun extractFlashcards(title: String, content: String, summary: String = ""): List<Flashcard> {
        val cards = mutableListOf<Flashcard>()
        val allLines = (summary.lines() + content.lines())
            .map { it.trim().replace(BULLET_PREFIX, "") }
            .filter { it.length >= 6 }
            .distinct()

        for (line in allLines) {
            val parts = line.split(KEY_VALUE_SPLIT, limit = 2)
            if (parts.size == 2 && parts[0].length in 2..60 && parts[1].length >= 3) {
                cards += Flashcard(front = parts[0].trim(), back = parts[1].trim())
            }
        }

        if (cards.isEmpty()) {
            val topic = title.trim().ifBlank { "Key point" }
            allLines.take(8).forEachIndexed { index, line ->
                cards += Flashcard(
                    front = "$topic (#${index + 1})",
                    back = line
                )
            }
        }
        return cards.distinct()
    }
}
