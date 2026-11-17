package com.group15.notemate.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.group15.notemate.model.Note
import java.io.File

/** MVVM: Model (repository). Where the ViewModels load and save the notes. */
interface NoteRepository {

    suspend fun getAll(): List<Note>

    suspend fun getById(id: Long): Note? = getAll().find { it.id == id }

    /** Adds the note (id 0: it gets a new id) or replaces the saved note with the same id. Returns the saved note. */
    suspend fun save(note: Note): Note

    suspend fun delete(id: Long)

    companion object {
        private const val LOCAL_FILE_NAME = "notes.json"

        @Volatile
        private var instance: NoteRepository? = null

        /** One repository for the whole app, so every screen sees the same notes. */
        fun getInstance(context: Context): NoteRepository =
            instance ?: synchronized(this) {
                instance ?: FirestoreNoteRepository(
                    auth = FirebaseAuth.getInstance(),
                    db = FirebaseFirestore.getInstance(),
                    oldLocalNotes = LocalNoteRepository(File(context.applicationContext.filesDir, LOCAL_FILE_NAME))
                ).also { instance = it }
            }
    }
}
