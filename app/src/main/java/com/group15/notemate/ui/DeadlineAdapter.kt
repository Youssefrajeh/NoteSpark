package com.group15.notemate.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.group15.notemate.R
import com.group15.notemate.databinding.ItemDeadlineBinding
import com.group15.notemate.model.Deadline

/** MVVM: View. RecyclerView adapter, one card per date found in the notes. */
class DeadlineAdapter(
    private val onClick: (Deadline) -> Unit,
    private val onCalendarClick: (Deadline) -> Unit
) : ListAdapter<Deadline, DeadlineAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDeadlineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemDeadlineBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(deadline: Deadline) {
            val context = binding.root.context
            binding.textDate.text = deadline.formatWhen()
            binding.textLine.text = deadline.line
            binding.textNote.text = context.getString(
                R.string.deadline_from_note,
                deadline.noteTitle.ifBlank { context.getString(R.string.untitled_note) }
            )
            binding.buttonCalendar.setOnClickListener { onCalendarClick(deadline) }
            binding.root.setOnClickListener { onClick(deadline) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Deadline>() {
            override fun areItemsTheSame(oldItem: Deadline, newItem: Deadline) = oldItem == newItem
            override fun areContentsTheSame(oldItem: Deadline, newItem: Deadline) = oldItem == newItem
        }
    }
}
