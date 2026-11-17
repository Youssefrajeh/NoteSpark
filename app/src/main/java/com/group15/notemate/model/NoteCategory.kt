package com.group15.notemate.model

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.group15.notemate.R

/** MVVM: Model. Optional category or priority badge attached to a note. */
enum class NoteCategory(
    val key: String,
    @param:StringRes val labelRes: Int,
    @param:ColorRes val bgColorRes: Int,
    @param:ColorRes val textColorRes: Int
) {
    LECTURE("Lecture", R.string.category_lecture, R.color.tag_info_bg, R.color.tag_info_text),
    ASSIGNMENT("Assignment", R.string.category_assignment, R.color.tag_soon_bg, R.color.tag_soon_text),
    EXAM("Exam", R.string.category_exam, R.color.tag_urgent_bg, R.color.tag_urgent_text),
    URGENT("Urgent", R.string.category_urgent, R.color.tag_follow_bg, R.color.tag_follow_text);

    companion object {
        fun fromKey(key: String?): NoteCategory? = entries.find { it.key.equals(key, ignoreCase = true) }
    }
}

