package com.group15.notemate.data

import android.util.Log
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.group15.notemate.R
import com.group15.notemate.model.MessageException
import com.group15.notemate.model.Note
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * MVVM: Model (repository). Saves the notes in Cloud Firestore, at users/{uid}/notes/{note id}.
 *
 * - Each phone signs in with an anonymous Firebase account the first time, so it only sees its own notes
 *   (the Firestore security rules check the uid).
 * - Firestore keeps a copy of the notes on the phone. Reads fall back to that copy without internet, and
 *   writes are not awaited: they show up in the app at once and Firestore sends them when the phone is
 *   back online.
 * - The first time, the notes the app saved before in files/notes.json are copied to Firestore, and the
 *   file is renamed to notes-migrated.json so it is kept as a backup.
 */
class FirestoreNoteRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val oldLocalNotes: LocalNoteRepository
) : NoteRepository {

    // One save at a time, so two new notes never get the same id
    private val mutex = Mutex()

    @Volatile
    private var migrated = false

    override suspend fun getAll(): List<Note> = mutex.withLock { readAll(notes()) }

    override suspend fun getById(id: Long): Note? = mutex.withLock {
        val document = cloud { notes().document(id.toString()).get().await() }
        if (document.exists()) document.toNote() else null
    }

    override suspend fun save(note: Note): Note = mutex.withLock {
        val notes = notes()
        val saved = if (note.id == 0L) note.copy(id = nextId(notes)) else note
        write(notes, saved)
        saved
    }

    override suspend fun delete(id: Long) = mutex.withLock {
        notes().document(id.toString()).delete()
            .addOnFailureListener { Log.w(TAG, "Could not delete note $id", it) }
        Unit
    }

    /** The signed-in user's notes collection. Signs in and moves the old local notes the first time. */
    private suspend fun notes(): CollectionReference {
        val user = auth.currentUser ?: cloud { auth.signInAnonymously().await().user }
            ?: throw MessageException(R.string.error_cloud_unavailable)
        val notes = db.collection(USERS).document(user.uid).collection(NOTES)
        if (!migrated) {
            moveOldLocalNotes(notes)
            migrated = true
        }
        return notes
    }

    private suspend fun readAll(notes: CollectionReference): List<Note> =
        cloud { notes.get().await() }.documents.map { it.toNote() }.sortedBy { it.id }

    private suspend fun nextId(notes: CollectionReference): Long =
        (readAll(notes).maxOfOrNull { it.id } ?: 0L) + 1

    /** Not awaited (see the class comment). A failure, such as a refused write, is only logged. */
    private fun write(notes: CollectionReference, note: Note) {
        notes.document(note.id.toString()).set(note.toMap())
            .addOnFailureListener { Log.w(TAG, "Could not save note ${note.id}", it) }
    }

    private suspend fun moveOldLocalNotes(notes: CollectionReference) {
        val file = oldLocalNotes.file
        if (!file.exists()) return
        val old = oldLocalNotes.getAll()
        if (old.isNotEmpty()) {
            val takenIds = readAll(notes).map { it.id }.toMutableSet()
            var next = (takenIds.maxOrNull() ?: 0L) + 1
            for (note in old) {
                // Keeps the old id unless a note in Firestore already has it
                val id = if (note.id != 0L && note.id !in takenIds) note.id else next++
                takenIds += id
                next = maxOf(next, id + 1)
                write(notes, note.copy(id = id))
            }
        }
        file.renameTo(File(file.parentFile, MIGRATED_FILE_NAME))
        Log.i(TAG, "Moved ${old.size} local notes to Firestore")
    }

    /** Turns Firebase errors (no internet on the first sign-in, refused by the rules…) into a clear message. */
    private suspend fun <T> cloud(block: suspend () -> T): T =
        try {
            block()
        } catch (e: FirebaseException) {
            Log.w(TAG, "Firebase error", e)
            throw MessageException(R.string.error_cloud_unavailable, e)
        }

    private fun Note.toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "title" to title,
        "content" to content,
        "course" to course,
        "category" to category,
        "pinned" to pinned,
        "summary" to summary,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    /** Missing fields get the same defaults as in [Note]. */
    private fun DocumentSnapshot.toNote() = Note(
        id = getLong("id") ?: id.toLongOrNull() ?: 0L,
        title = getString("title").orEmpty(),
        content = getString("content").orEmpty(),
        course = getString("course").orEmpty(),
        category = getString("category").orEmpty(),
        pinned = getBoolean("pinned") ?: false,
        summary = getString("summary").orEmpty(),
        createdAt = getLong("createdAt") ?: 0L,
        updatedAt = getLong("updatedAt") ?: 0L
    )

    private companion object {
        const val TAG = "FirestoreNoteRepository"
        const val USERS = "users"
        const val NOTES = "notes"
        const val MIGRATED_FILE_NAME = "notes-migrated.json"
    }
}
