package com.group15.notemate.model

import androidx.annotation.StringRes

/** An expected problem with a message for the user. The ViewModels show [messageRes] in a Snackbar. */
open class MessageException(@param:StringRes val messageRes: Int, cause: Throwable? = null) : Exception(cause)

