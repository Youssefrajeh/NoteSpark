package com.group15.notemate.viewmodel

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.group15.notemate.data.NoteRepository
import com.group15.notemate.ml.NoteAnalyzer
import com.group15.notemate.model.Dates
import com.group15.notemate.model.Deadline
import com.group15.notemate.model.Note

/** MVVM: ViewModel. Upcoming deadlines: the dates and times Entity Extraction finds in all the notes, from today on. */
class DeadlinesViewModel(application: Application) : BaseViewModel(application) {

    private val repository = NoteRepository.getInstance(application)
    private val analyzer = NoteAnalyzer(application)

    /** Null until the notes have been read for the first time. */
    private val _deadlines = MutableLiveData<List<Deadline>?>()
    val deadlines: LiveData<List<Deadline>?> = _deadlines

    /**
     * The dates already found, per note id, with the text they were found in. The screen loads again every
     * time it comes back, so only the notes added or changed since the last time go through Entity Extraction.
     */
    private val cache = mutableMapOf<Long, CachedNote>()

    private data class CachedNote(val title: String, val content: String, val deadlines: List<Deadline>)

    fun load() = launchWork {
        val notes = repository.getAll()
        val changed = notes.filter { note -> !cache[note.id].isFor(note) }
        if (changed.isNotEmpty()) {
            val found = analyzer.findDeadlines(changed).groupBy { it.noteId }
            for (note in changed) cache[note.id] = CachedNote(note.title, note.content, found[note.id].orEmpty())
        }
        // Forget the notes that were deleted
        cache.keys.retainAll(notes.map { it.id }.toSet())

        val today = Dates.startOfDay(System.currentTimeMillis())
        _deadlines.value = cache.values.flatMap { it.deadlines }.filter { it.millis >= today }.sortedBy { it.millis }
    }

    private fun CachedNote?.isFor(note: Note) = this != null && title == note.title && content == note.content
}
