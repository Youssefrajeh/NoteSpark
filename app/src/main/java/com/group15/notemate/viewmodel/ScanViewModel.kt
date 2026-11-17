package com.group15.notemate.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.group15.notemate.R
import com.group15.notemate.ml.NoteAnalyzer

/** MVVM: ViewModel. Scan screen: one or more scanned pages or a picked image -> Text Recognition -> text for the note. */
class ScanViewModel(application: Application) : BaseViewModel(application) {

    private val analyzer = NoteAnalyzer(application)

    private val _imageUri = MutableLiveData<Uri?>()
    val imageUri: LiveData<Uri?> = _imageUri

    /** The text read from the image(s), once: the screen puts it in a text field the student can correct. */
    private val _recognized = MutableLiveData<Event<String>>()
    val recognized: LiveData<Event<String>> = _recognized

    /** Shows a single picked image and reads its text. */
    fun readImage(uri: Uri) = readImages(listOf(uri))

    /** Reads one or more scanned pages in order. Text that may be wrong is still shown, with a warning. */
    fun readImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _imageUri.value = uris.first()
        launchWork {
            val result = analyzer.readTextFromImages(uris)
            _recognized.value = Event(result.text)
            if (!result.readable) showMessage(R.string.warning_scan_unclear)
        }
    }
}
