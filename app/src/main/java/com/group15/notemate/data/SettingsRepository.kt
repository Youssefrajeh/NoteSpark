package com.group15.notemate.data

import android.content.Context
import androidx.core.content.edit

/** MVVM: Model (repository). Small settings saved in SharedPreferences. */
class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The course picked in the filter on the main screen; empty for "All courses". */
    var courseFilter: String
        get() = prefs.getString(KEY_COURSE_FILTER, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_COURSE_FILTER, value) }

    private companion object {
        const val PREFS_NAME = "notemate_prefs"
        const val KEY_COURSE_FILTER = "course_filter"
    }
}
