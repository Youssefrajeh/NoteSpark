package com.group15.notemate.data

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import com.group15.notemate.R
import com.group15.notemate.model.MessageException
import com.group15.notemate.model.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The notes saved as JSON with Gson in internal storage (files/notes.json), as the app did before Firestore.
 * [FirestoreNoteRepository] now only uses it once, to move these notes to Firestore. Every function runs on
 * Dispatchers.IO.
 *
 * Saves are atomic: the new list goes to a temporary file that then replaces notes.json, so a crash
 * or a dead battery in the middle of a save never leaves a half-written notes.json behind.
 */
class LocalNoteRepository(val file: File) : NoteRepository {

    private val gson = Gson()

    // Only one coroutine reads or writes the file at a time
    private val mutex = Mutex()

    override suspend fun getAll(): List<Note> = withContext(Dispatchers.IO) {
        mutex.withLock { read() }
    }

    /** Adds the note (id 0: it gets a new id) or replaces the saved note with the same id. Returns the saved note. */
    override suspend fun save(note: Note): Note = withContext(Dispatchers.IO) {
        mutex.withLock {
            val list = read().toMutableList()
            val saved = if (note.id == 0L) note.copy(id = (list.maxOfOrNull { it.id } ?: 0L) + 1) else note
            val index = list.indexOfFirst { it.id == saved.id }
            if (index >= 0) list[index] = saved else list.add(saved)
            write(list)
            saved
        }
    }

    override suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        mutex.withLock { write(read().filterNot { it.id == id }) }
    }

    /**
     * Reads every saved note. Never returns an empty list in place of notes it could not read, because the
     * next save would then overwrite them:
     * - an IOException goes up to the ViewModel, which shows it, and nothing is written;
     * - a damaged file is moved aside to notes-damaged-<time>.json (so it can still be recovered) and a
     *   [MessageException] tells the student.
     */
    private fun read(): List<Note> {
        if (!file.exists()) return emptyList()
        val text = file.readText()
        return try {
            gson.fromJson<List<Note?>>(text, NOTE_LIST_TYPE)?.filterNotNull().orEmpty()
        } catch (e: JsonParseException) {
            val damaged = File(file.parentFile, "notes-damaged-${System.currentTimeMillis()}.json")
            Log.w(TAG, "Could not parse $file, moved it to $damaged", e)
            file.copyTo(damaged, overwrite = true)
            file.delete()
            throw MessageException(R.string.error_notes_damaged, e)
        }
    }

    /**
     * Writes the whole list to a temporary file, flushes it to the disk, then renames it over notes.json in
     * one step. An IOException goes up to the ViewModel, which shows it, so a note is never lost silently.
     */
    private fun write(list: List<Note>) {
        val temp = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(temp).use { out ->
            out.write(gson.toJson(list).toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    companion object {
        private const val TAG = "LocalNoteRepository"
        private val NOTE_LIST_TYPE = object : TypeToken<List<Note?>>() {}.type
    }
}
