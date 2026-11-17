package com.group15.notemate.viewmodel

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.group15.notemate.data.NoteRepository
import com.group15.notemate.data.SettingsRepository
import com.group15.notemate.model.Note
import com.group15.notemate.model.NoteFilter

/** MVVM: ViewModel. Main screen: the list of notes with search, the course filter, pinning and swipe-to-delete. */
class NotesViewModel(application: Application) : BaseViewModel(application) {

    private val repository = NoteRepository.getInstance(application)
    private val settings = SettingsRepository(application)

    private var allNotes = emptyList<Note>()
    private var loaded = false
    private var query = ""

    /** The course picked in the filter, saved in SharedPreferences; empty for all courses. */
    var courseFilter = settings.courseFilter
        private set

    /** The notes that match the search and the course filter: pinned first, then newest first. */
    private val _notes = MutableLiveData<List<Note>>()
    val notes: LiveData<List<Note>> = _notes

    /** Every course that has at least one note, for the filter. */
    private val _courses = MutableLiveData<List<String>>()
    val courses: LiveData<List<String>> = _courses

    /** Emits a swiped-to-delete note once so the screen can offer an Undo Snackbar. */
    private val _deletedNote = MutableLiveData<Event<Note>>()
    val deletedNote: LiveData<Event<Note>> = _deletedNote

    /** False when nothing is saved at all (and not only hidden by the search or the filter). */
    val hasNotes: Boolean get() = allNotes.isNotEmpty()

    fun load() = launchWork {
        refreshFromRepository()
    }

    fun setQuery(text: String) {
        query = text.trim()
        publish()
    }

    fun setCourse(course: String) {
        if (course == courseFilter) return
        saveFilter(course)
        publish()
    }

    fun togglePin(note: Note) = launchWork {
        repository.save(note.copy(pinned = !note.pinned))
        refreshFromRepository()
    }

    fun deleteWithUndo(note: Note) = launchWork {
        repository.delete(note.id)
        refreshFromRepository()
        _deletedNote.value = Event(note)
    }

    fun restore(note: Note) = launchWork {
        repository.save(note)
        refreshFromRepository()
    }

    private suspend fun refreshFromRepository() {
        allNotes = repository.getAll()
        loaded = true
        val courses = allNotes.map { it.course }.filter { it.isNotBlank() }.distinct().sorted()
        // The last note of the selected course may have been deleted or moved
        if (courseFilter !in courses) saveFilter("")
        _courses.value = courses
        publish()
    }

    private fun saveFilter(course: String) {
        courseFilter = course
        settings.courseFilter = course
    }

    private fun publish() {
        // Nothing to show before the file has been read (the search field can call setQuery first)
        if (!loaded) return
        _notes.value = NoteFilter.apply(allNotes, courseFilter, query)
    }
}
