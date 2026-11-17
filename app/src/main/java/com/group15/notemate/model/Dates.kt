package com.group15.notemate.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** MVVM: Model helper. Date maths and formatting with java.time. */
object Dates {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private fun toLocalDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun startOfDay(millis: Long): Long = toLocalDate(millis).atStartOfDay(zone).toInstant().toEpochMilli()

    /** The calendar app wants all-day events to start at midnight UTC of that day. */
    fun utcMidnight(millis: Long): Long = toLocalDate(millis).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun formatDate(millis: Long): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()).format(toLocalDate(millis))

    fun formatDateTime(millis: Long): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
            .format(Instant.ofEpochMilli(millis).atZone(zone))
}
