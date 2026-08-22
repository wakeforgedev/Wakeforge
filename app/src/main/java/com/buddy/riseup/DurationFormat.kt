package com.buddy.riseup

/** Shared by the add-alarm dialog and the alarm list so "60 min" reads as "1 hour" everywhere. */
fun formatDurationMinutes(minutes: Int): String =
    if (minutes % 60 == 0) {
        val hours = minutes / 60
        if (hours == 1) "1 hour" else "$hours hours"
    } else {
        "$minutes min"
    }
