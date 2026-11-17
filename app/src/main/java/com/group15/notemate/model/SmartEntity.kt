package com.group15.notemate.model

/** MVVM: Model. An actionable entity (email, phone number, web link or address) found by ML Kit Entity Extraction. */
data class SmartEntity(
    val type: Type,
    val text: String
) {
    enum class Type { EMAIL, PHONE, URL, ADDRESS }
}

