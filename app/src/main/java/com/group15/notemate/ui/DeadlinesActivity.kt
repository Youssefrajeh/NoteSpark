package com.group15.notemate.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.group15.notemate.R
import com.group15.notemate.databinding.ActivityDeadlinesBinding
import com.group15.notemate.viewmodel.DeadlinesViewModel

/**
 * MVVM: View. Upcoming deadlines: the dates and times found in all the notes with Entity Extraction.
 * A date can be added to the calendar; tapping a card opens its note.
 */
class DeadlinesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeadlinesBinding
    private val viewModel: DeadlinesViewModel by viewModels()

    /** The list slides in once, not every time the screen comes back and loads again. */
    private var listAnimated = false

    private val adapter = DeadlineAdapter(
        onClick = { deadline ->
            openScreen(Intent(this, EditorActivity::class.java).putExtra(EditorActivity.EXTRA_NOTE_ID, deadline.noteId))
        },
        onCalendarClick = { deadline -> addToCalendar(deadline) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityDeadlinesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        observeMessages(viewModel)

        binding.recyclerDeadlines.adapter = adapter
        binding.empty.imageEmpty.setImageResource(R.drawable.ic_event)
        binding.empty.textEmptyTitle.setText(R.string.deadlines_empty_title)
        binding.empty.textEmptyMessage.setText(R.string.deadlines_empty_message)

        viewModel.isLoading.observe(this) { loading -> binding.progress.isVisible = loading }
        viewModel.deadlines.observe(this) { deadlines ->
            adapter.submitList(deadlines.orEmpty()) {
                // Layout animation: the first time the list is shown, the cards slide up one after the other
                if (!listAnimated && !deadlines.isNullOrEmpty()) {
                    listAnimated = true
                    binding.recyclerDeadlines.scheduleLayoutAnimation()
                }
            }
            // Null: the notes have not been read yet, so "no deadlines" is not known
            binding.empty.root.isVisible = deadlines != null && deadlines.isEmpty()
        }
    }

    override fun onResume() {
        super.onResume()
        // A note may have been changed on the editor screen
        viewModel.load()
    }

    override fun onSupportNavigateUp(): Boolean {
        finishAfterTransition()
        return true
    }
}
