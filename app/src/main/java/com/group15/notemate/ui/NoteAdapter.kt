package com.group15.notemate.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.group15.notemate.R
import com.group15.notemate.databinding.ItemNoteBinding
import com.group15.notemate.model.Dates
import com.group15.notemate.model.Note
import com.group15.notemate.model.NoteCategory

/** MVVM: View. RecyclerView adapter, one card per note on the main screen. */
class NoteAdapter(
    private val onClick: (Note) -> Unit,
    private val onPinClick: (Note) -> Unit
) : ListAdapter<Note, NoteAdapter.ViewHolder>(DIFF) {

    fun getNoteAt(position: Int): Note = getItem(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemNoteBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(note: Note) {
            val context = binding.root.context
            binding.textTitle.text = note.displayTitle.ifBlank { context.getString(R.string.untitled_note) }

            // The start of the note on one line
            val preview = note.content.trim().replace(Regex("\\s+"), " ")
            binding.textPreview.text = preview
            binding.textPreview.isVisible = preview.isNotEmpty()

            binding.textCourse.text = note.course
            binding.textCourse.isVisible = note.course.isNotBlank()

            val category = NoteCategory.fromKey(note.category)
            binding.textCategory.isVisible = category != null
            if (category != null) {
                binding.textCategory.text = context.getString(category.labelRes)
                binding.textCategory.backgroundTintList = ColorStateList.valueOf(context.getColor(category.bgColorRes))
                binding.textCategory.setTextColor(context.getColor(category.textColorRes))
            }

            binding.textDate.text = Dates.formatDate(note.updatedAt)

            // The pin is faded on notes that are not pinned
            binding.buttonPin.alpha = if (note.pinned) 1f else UNPINNED_ALPHA
            binding.buttonPin.contentDescription =
                context.getString(if (note.pinned) R.string.unpin_note else R.string.pin_note)
            binding.buttonPin.setOnClickListener { pin ->
                // Tween animation: the pin pops when it is tapped
                pin.play(R.anim.pop)
                onPinClick(note)
            }
            binding.root.setOnClickListener { onClick(note) }
        }
    }

    private companion object {
        const val UNPINNED_ALPHA = 0.3f

        val DIFF = object : DiffUtil.ItemCallback<Note>() {
            override fun areItemsTheSame(oldItem: Note, newItem: Note) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Note, newItem: Note) = oldItem == newItem
        }
    }
}
