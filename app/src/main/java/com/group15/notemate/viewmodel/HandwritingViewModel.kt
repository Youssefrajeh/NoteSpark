package com.group15.notemate.viewmodel

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.group15.notemate.R
import com.group15.notemate.ml.AnalyzerException
import com.group15.notemate.ml.NoteAnalyzer
import com.group15.notemate.model.StrokePoint

/** MVVM: ViewModel. Handwriting screen: keeps the strokes drawn on the screen and converts them with Digital Ink Recognition. */
class HandwritingViewModel(application: Application) : BaseViewModel(application) {

    private val analyzer = NoteAnalyzer(application)

    /** The strokes on the canvas. Kept here so the drawing survives a rotation. */
    private val _strokes = MutableLiveData<List<List<StrokePoint>>>(emptyList())
    val strokes: LiveData<List<List<StrokePoint>>> = _strokes

    /** One recognized line, once: the screen adds it to a text field the student can correct. */
    private val _recognized = MutableLiveData<Event<String>>()
    val recognized: LiveData<Event<String>> = _recognized

    fun addStroke(stroke: List<StrokePoint>) {
        _strokes.value = _strokes.value.orEmpty() + listOf(stroke)
    }

    fun clear() {
        _strokes.value = emptyList()
    }

    /** Converts the drawing to text and clears the canvas for the next line. */
    fun recognize() {
        val drawn = _strokes.value.orEmpty()
        if (drawn.isEmpty()) return showMessage(R.string.error_nothing_drawn)
        launchWork {
            val text = analyzer.recognizeHandwriting(drawn)
            if (text.isBlank()) throw AnalyzerException(R.string.error_handwriting_not_read)
            _recognized.value = Event(text)
            clear()
        }
    }
}
