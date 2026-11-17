package com.group15.notemate.ui

import android.content.Intent
import android.graphics.drawable.AnimationDrawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.group15.notemate.R
import com.group15.notemate.databinding.ActivityMainBinding
import com.group15.notemate.model.Note
import com.group15.notemate.viewmodel.NotesViewModel

/** MVVM: View. Main screen: search, the course filter (Spinner) and the list of notes, pinned notes first. */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: NotesViewModel by viewModels()

    /** The list slides in once per screen, not every time the search or the filter changes it. */
    private var listAnimated = false

    private val adapter = NoteAdapter(
        onClick = { note -> openEditor(note.id) },
        onPinClick = { note -> viewModel.togglePin(note) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()
        setSupportActionBar(binding.toolbar)
        observeMessages(viewModel)

        binding.recyclerNotes.adapter = adapter
        attachSwipeToDelete()
        binding.empty.imageEmpty.setImageResource(R.drawable.anim_writing)
        binding.fabNewNote.setOnClickListener { openEditor(NEW_NOTE) }
        // Tween animation: the button spins in when the app opens (not again after a rotation)
        if (savedInstanceState == null) binding.fabNewNote.play(R.anim.fab_in)
        binding.editSearch.doAfterTextChanged { viewModel.setQuery(it.toString()) }
        binding.spinnerCourse.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                // The first item is "All courses"
                viewModel.setCourse(if (position == 0) "" else parent.getItemAtPosition(position) as String)
            }

            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }

        viewModel.courses.observe(this, ::showCourses)
        viewModel.notes.observe(this, ::showNotes)
        viewModel.deletedNote.observe(this) { event ->
            event.getContentIfNotHandled()?.let(::showUndoDelete)
        }
    }

    override fun onResume() {
        super.onResume()
        // Notes are added, changed and deleted on the editor screen
        viewModel.load()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_deadlines -> {
            openScreen(Intent(this, DeadlinesActivity::class.java))
            true
        }
        R.id.action_about -> {
            openScreen(Intent(this, AboutActivity::class.java))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun attachSwipeToDelete() {
        val callback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    viewModel.deleteWithUndo(adapter.getNoteAt(position))
                }
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding.recyclerNotes)
    }

    private fun showUndoDelete(deleted: Note) {
        Snackbar.make(findViewById(android.R.id.content), R.string.note_deleted, Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) { viewModel.restore(deleted) }
            .show()
    }

    private fun openEditor(noteId: Long) {
        openScreen(Intent(this, EditorActivity::class.java).putExtra(EditorActivity.EXTRA_NOTE_ID, noteId))
    }

    private fun showCourses(courses: List<String>) {
        val items = listOf(getString(R.string.filter_all_courses)) + courses
        binding.spinnerCourse.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        // indexOf is -1 when no course is selected, which gives 0: "All courses"
        binding.spinnerCourse.setSelection(courses.indexOf(viewModel.courseFilter) + 1)
    }

    private fun showNotes(notes: List<Note>) {
        adapter.submitList(notes) {
            // Layout animation: the first time the list is shown, the cards slide up one after the other
            if (!listAnimated && notes.isNotEmpty()) {
                listAnimated = true
                binding.recyclerNotes.scheduleLayoutAnimation()
            }
        }
        binding.empty.root.isVisible = notes.isEmpty()
        // Frame animation: the page in the empty state is written line by line
        val writing = binding.empty.imageEmpty.drawable as? AnimationDrawable
        if (notes.isEmpty()) writing?.start() else writing?.stop()
        if (notes.isNotEmpty()) return
        // Either nothing is saved yet, or the search and the filter hide every note
        val filtered = viewModel.hasNotes
        binding.empty.textEmptyTitle.setText(if (filtered) R.string.empty_filtered_title else R.string.empty_title)
        binding.empty.textEmptyMessage.setText(if (filtered) R.string.empty_filtered_message else R.string.empty_message)
    }

    private companion object {
        const val NEW_NOTE = 0L
    }
}
