package com.group15.notemate.model

/**
 * MVVM: Model. The text Text Recognition read in a scanned page or a picture.
 * [readable] is false when Language ID could not tell which language it is, which usually means
 * the recognizer guessed at letters (handwriting, a blurry photo or a script it does not read).
 */
data class RecognizedText(val text: String, val readable: Boolean)

