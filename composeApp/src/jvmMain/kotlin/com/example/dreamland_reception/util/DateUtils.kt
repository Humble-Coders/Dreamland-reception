package com.example.dreamland_reception.util

import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

fun Date.toMidnightUtc(): Date = Date(
    LocalDate.ofInstant(this.toInstant(), ZoneOffset.UTC)
        .atStartOfDay()
        .toInstant(ZoneOffset.UTC)
        .toEpochMilli()
)

fun Date.atHotelTime(timeStr: String): Long {
    if (timeStr.isBlank()) return this.time
    val parts = timeStr.split(":")
    val hour   = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return Calendar.getInstance().apply {
        time = this@atHotelTime
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

// month is 0-based (Calendar convention). Callers with 1-based months must pass month - 1.
fun dateFromPicker(year: Int, month: Int, day: Int): Date =
    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        set(year, month, day, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.time
