package com.group15.notemate.ml

import com.group15.notemate.model.Flashcard
import org.junit.Assert.assertEquals
import org.junit.Test

class TextHelpersTest {

    private fun sentenceOf(text: String, date: String): String {
        val start = text.indexOf(date)
        return TextHelpers.sentenceAround(text, start, start + date.length)
    }

    @Test
    fun keepsOnlyTheSentenceWithTheDate() {
        val text = "Read chapter 3. Assignment 2 due Nov 28. Bring a laptop."
        assertEquals("Assignment 2 due Nov 28.", sentenceOf(text, "Nov 28"))
    }

    @Test
    fun abbreviationInsideTheDate_isNotCut() {
        val text = "Lab report due Nov. 28 at noon. Good luck"
        assertEquals("Lab report due Nov. 28 at noon.", sentenceOf(text, "Nov. 28 at noon"))
    }

    @Test
    fun dateEndingWithFullStop_keepsTheSentence() {
        val text = "Quiz on Friday at 5pm. Then the break"
        assertEquals("Quiz on Friday at 5pm.", sentenceOf(text, "Friday at 5pm."))
    }

    @Test
    fun stopsAtLineBreaks() {
        val text = "Week 9\nMidterm on December 3\nStudy groups"
        assertEquals("Midterm on December 3", sentenceOf(text, "December 3"))
    }

    @Test
    fun wordCount_andReadingMinutes() {
        assertEquals(0, TextHelpers.wordCount("   "))
        assertEquals(0, TextHelpers.readingMinutes(0))
        assertEquals(4, TextHelpers.wordCount("Coroutines run on Dispatchers"))
        assertEquals(1, TextHelpers.readingMinutes(4))
        assertEquals(2, TextHelpers.readingMinutes(250))
    }

    @Test
    fun extractFlashcards_parsesDefinitionsAndFallbackLines() {
        val defs = "• Coroutine: A light-weight thread\nViewModel - Holds UI state"
        assertEquals(
            listOf(
                Flashcard("Coroutine", "A light-weight thread"),
                Flashcard("ViewModel", "Holds UI state")
            ),
            TextHelpers.extractFlashcards("Kotlin", defs)
        )

        val fallback = TextHelpers.extractFlashcards("Biology", "Mitochondria power the cell")
        assertEquals(listOf(Flashcard("Biology (#1)", "Mitochondria power the cell")), fallback)
    }
}
