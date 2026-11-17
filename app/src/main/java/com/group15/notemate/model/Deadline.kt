package com.group15.notemate.model

/** MVVM: Model. A date or time that Entity Extraction found in a note. */
data class Deadline(
    val noteId: Long,
    val noteTitle: String,
    /** The sentence of the note the date was found in, e.g. "Assignment 2 due Nov 28". */
    val line: String,
    val millis: Long,
    /** False when the note only gives a day ("Nov 28"), true when it also gives a time ("Nov 28 at 5pm"). */
    val hasTime: Boolean
)
