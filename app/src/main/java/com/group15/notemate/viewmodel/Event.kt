package com.group15.notemate.viewmodel

/** MVVM: ViewModel helper. A LiveData value that is handled only once (a message is not shown again after rotation). */
class Event<out T>(private val content: T) {

    private var handled = false

    fun getContentIfNotHandled(): T? {
        if (handled) return null
        handled = true
        return content
    }
}
