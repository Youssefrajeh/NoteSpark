package com.group15.notemate.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteFilterTest {

    private val math = Note(id = 1, title = "Limits", content = "Derivatives next week", course = "MATH-101", category = "Lecture", updatedAt = 10)
    private val mobile = Note(id = 2, title = "Coroutines", content = "viewModelScope", course = "INFO-5126", category = "Assignment", updatedAt = 30)
    private val pinned = Note(id = 3, title = "Midterm", content = "Room 204", course = "MATH-101", category = "Exam", pinned = true, updatedAt = 5)
    private val notes = listOf(math, mobile, pinned)

    @Test
    fun noFilter_pinnedFirstThenNewest() {
        assertEquals(listOf(pinned, mobile, math), NoteFilter.apply(notes, course = "", query = ""))
    }

    @Test
    fun courseFilter_keepsOnlyThatCourse() {
        assertEquals(listOf(pinned, math), NoteFilter.apply(notes, course = "MATH-101", query = ""))
    }

    @Test
    fun query_searchesTitleContentCourseAndCategory_ignoringCase() {
        assertEquals(listOf(math), NoteFilter.apply(notes, course = "", query = "DERIVATIVES"))
        assertEquals(listOf(mobile), NoteFilter.apply(notes, course = "", query = "coroutines"))
        assertEquals(listOf(mobile), NoteFilter.apply(notes, course = "", query = " info-5126 "))
        assertEquals(listOf(pinned), NoteFilter.apply(notes, course = "", query = "exam"))
    }

    @Test
    fun courseAndQuery_bothApply() {
        assertEquals(emptyList<Note>(), NoteFilter.apply(notes, course = "INFO-5126", query = "exam"))
    }
}

