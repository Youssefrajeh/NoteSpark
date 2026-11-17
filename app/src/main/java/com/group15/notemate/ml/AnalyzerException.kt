package com.group15.notemate.ml

import androidx.annotation.StringRes
import com.group15.notemate.model.MessageException

/** An expected ML problem (no text found, unsupported language...) with a message for the user. */
class AnalyzerException(@StringRes messageRes: Int, cause: Throwable? = null) : MessageException(messageRes, cause)
