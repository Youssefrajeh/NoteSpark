package com.group15.notemate.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.group15.notemate.R
import com.group15.notemate.data.NoteExporter
import com.group15.notemate.data.NoteRepository
import com.group15.notemate.ml.NoteAnalyzer
import com.group15.notemate.model.Deadline
import com.group15.notemate.model.Note
import com.group15.notemate.model.SmartEntity
import com.group15.notemate.model.TranslatedText

/** MVVM: ViewModel. Note editor: load, save, pin and delete a note, export PDF, and the ML tools (summary, translation, dates). */
class EditorViewModel(application: Application) : BaseViewModel(application) {

    private val repository = NoteRepository.getInstance(application)
    private val analyzer = NoteAnalyzer(application)

    /** The note as it is saved; a new Note() until a new note is saved for the first time. */
    private var note = Note()
    private var started = false

    // A second tap on Save while the file is being written must not add the note twice
    private var saving = false

    /** The saved note, once, to fill the text fields (after a rotation the fields keep their own text). */
    private val _loaded = MutableLiveData<Event<Note>>()
    val loaded: LiveData<Event<Note>> = _loaded

    private val _pinned = MutableLiveData(false)
    val pinned: LiveData<Boolean> = _pinned

    private val _summary = MutableLiveData("")
    val summary: LiveData<String> = _summary

    private val _translation = MutableLiveData<TranslatedText?>()
    val translation: LiveData<TranslatedText?> = _translation

    private val _deadlines = MutableLiveData<Event<Pair<List<Deadline>, List<SmartEntity>>>>()
    val deadlines: LiveData<Event<Pair<List<Deadline>, List<SmartEntity>>>> = _deadlines

    /** True when the screen should close: the note was saved or deleted. */
    private val _closed = MutableLiveData(false)
    val closed: LiveData<Boolean> = _closed

    /** True when the note to edit no longer exists: the screen tells the student and closes. */
    private val _noteMissing = MutableLiveData(false)
    val noteMissing: LiveData<Boolean> = _noteMissing

    val isNewNote: Boolean get() = note.id == 0L

    /** Loads the note with [id] the first time the screen opens; 0 starts a new note. */
    fun start(id: Long) {
        if (started) return
        started = true
        if (id == 0L) return
        launchWork {
            val found = repository.getById(id)
            if (found == null) {
                // Deleted in the meantime: close instead of letting Save create it again as a new note
                _noteMissing.value = true
                return@launchWork
            }
            note = found
            _pinned.value = found.pinned
            _summary.value = found.summary
            _loaded.value = Event(found)
        }
    }

    /** Saves what is on the screen and closes it. An empty new note is not saved. */
    fun saveAndClose(title: String, course: String, category: String, content: String) {
        if (saving) return
        val edited = note.copy(
            title = title.trim(),
            course = course.trim(),
            category = category.trim(),
            content = content,
            pinned = _pinned.value == true,
            summary = _summary.value.orEmpty()
        )
        val emptyNewNote = isNewNote && edited.title.isEmpty() && edited.content.isBlank()
        if (emptyNewNote || edited == note) {
            _closed.value = true
            return
        }
        saving = true
        launchWork {
            try {
                val now = System.currentTimeMillis()
                note = repository.save(edited.copy(createdAt = if (isNewNote) now else note.createdAt, updatedAt = now))
                _closed.value = true
            } finally {
                saving = false
            }
        }
    }

    fun togglePin() {
        _pinned.value = _pinned.value != true
    }

    fun delete() = launchWork {
        if (!isNewNote) repository.delete(note.id)
        _closed.value = true
    }

    fun exportPdf(uri: Uri, title: String, course: String, category: String, content: String) = launchWork {
        NoteExporter.writePdf(
            context = getApplication(),
            uri = uri,
            title = title,
            course = course,
            category = category,
            content = content,
            summary = _summary.value.orEmpty()
        )
        showMessage(R.string.pdf_saved)
    }

    /** Gemini Nano summary of the text on the screen. It is saved with the note. */
    fun summarize(content: String) {
        if (content.isBlank()) return showMessage(R.string.error_note_empty)
        launchWork { _summary.value = analyzer.summarize(content) }
    }

    fun translate(content: String) {
        if (content.isBlank()) return showMessage(R.string.error_note_empty)
        launchWork { _translation.value = analyzer.translateToEnglish(content) }
    }

    /** Called after the translation replaced the note text, so its card is hidden. */
    fun clearTranslation() {
        _translation.value = null
    }

    /** Entity Extraction on the text on the screen (dates, emails, phone numbers, URLs and addresses). */
    fun findDeadlines(title: String, content: String) {
        if (title.isBlank() && content.isBlank()) return showMessage(R.string.error_note_empty)
        launchWork {
            val foundDates = analyzer.findDeadlines(listOf(note.copy(title = title, content = content)))
            val foundEntities = analyzer.findSmartEntities("$title\n$content")
            if (foundDates.isEmpty() && foundEntities.isEmpty()) {
                showMessage(R.string.no_dates_found)
            } else {
                _deadlines.value = Event(foundDates to foundEntities)
            }
        }
    }
}
