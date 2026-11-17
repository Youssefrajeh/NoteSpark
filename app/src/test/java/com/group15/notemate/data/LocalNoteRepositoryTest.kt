package com.group15.notemate.data

import com.group15.notemate.model.MessageException
import com.group15.notemate.model.Note
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocalNoteRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var file: File
    private lateinit var repository: LocalNoteRepository

    @Before
    fun setUp() {
        file = File(folder.root, "notes.json")
        repository = LocalNoteRepository(file)
    }

    @Test
    fun noFile_noNotes() = runBlocking {
        assertEquals(emptyList<Note>(), repository.getAll())
    }

    @Test
    fun save_givesNewIds_andReplacesBySameId() = runBlocking {
        val first = repository.save(Note(title = "A"))
        val second = repository.save(Note(title = "B"))
        assertEquals(1L, first.id)
        assertEquals(2L, second.id)

        repository.save(first.copy(title = "A2"))
        assertEquals(listOf("A2", "B"), repository.getAll().map { it.title })
    }

    @Test
    fun delete_removesOnlyThatNote() = runBlocking {
        val first = repository.save(Note(title = "A"))
        repository.save(Note(title = "B"))
        repository.delete(first.id)
        assertEquals(listOf("B"), repository.getAll().map { it.title })
    }

    @Test
    fun notesSurviveANewRepository() = runBlocking {
        repository.save(Note(title = "Kept", course = "INFO-5126"))
        val reopened = LocalNoteRepository(file).getAll().single()
        assertEquals("Kept", reopened.title)
        assertEquals("INFO-5126", reopened.course)
    }

    @Test
    fun missingFieldsInOldFiles_getTheirDefaults() = runBlocking {
        // A note saved before the summary field was added
        file.writeText("""[{"id":4,"title":"Old","content":"x"}]""")
        val note = repository.getAll().single()
        assertEquals("", note.summary)
        assertEquals("", note.course)
    }

    @Test
    fun damagedFile_isKeptAside_andNeverOverwritten() = runBlocking {
        val damaged = """[{"id":1,"title":"Important"""
        file.writeText(damaged)

        try {
            repository.save(Note(title = "New"))
            fail("A damaged file must stop the save")
        } catch (_: MessageException) {
        }

        // The damaged notes are still on disk, in a copy
        val copies = folder.root.listFiles { f -> f.name.startsWith("notes-damaged-") }.orEmpty()
        assertEquals(1, copies.size)
        assertEquals(damaged, copies.single().readText())

        // Then the app can carry on with a new list
        repository.save(Note(title = "New"))
        assertEquals(listOf("New"), repository.getAll().map { it.title })
        assertTrue(copies.single().exists())
    }
}

