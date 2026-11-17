package com.group15.notemate.model

/** MVVM: Model. One note, saved in Firestore by the FirestoreNoteRepository. An id of 0 means "not saved yet". */
data class Note(
    val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val course: String = "",
    /** Optional category/priority badge (e.g. Lecture, Assignment, Exam, Urgent). */
    val category: String = "",
    val pinned: Boolean = false,
    /** Made by Gemini Nano on the editor screen; empty until the student asks for it. */
    val summary: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0
) {
    /** The title, or the first line of the note when it has no title. */
    val displayTitle: String
        get() = title.ifBlank { content.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty() }
}
