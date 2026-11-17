package com.group15.notemate.model

/** MVVM: Model helper. The search and course filter of the main screen, kept apart so it can be unit tested. */
object NoteFilter {

    /**
     * The notes of [course] (empty: every course) whose title, text, course or category contains [query]
     * (empty: every note), ignoring case. Pinned notes first, then the most recently changed.
     */
    fun apply(notes: List<Note>, course: String, query: String): List<Note> {
        val search = query.trim()
        return notes
            .filter { course.isEmpty() || it.course == course }
            .filter { note ->
                search.isEmpty() || listOf(note.title, note.content, note.course, note.category.orEmpty())
                    .any { it.contains(search, ignoreCase = true) }
            }
            .sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
    }
}
