package io.onema.divetelemetry.util

/**
 * Parses a Shearwater start time string into seconds since midnight.
 *
 * @param dateStr Date string in `"M/d/yyyy h:mm:ss AM/PM"` format.
 * @return Seconds since midnight, or `null` if the string cannot be parsed.
 */
fun parseStartTimeSeconds(dateStr: String): Long? {
    val parts = dateStr.trim().split(" ")
    if (parts.size != 3) return null
    val timeParts = parts[1].split(":")
    if (timeParts.size != 3) return null
    val hour = timeParts[0].toIntOrNull() ?: return null
    val minute = timeParts[1].toIntOrNull() ?: return null
    val second = timeParts[2].toIntOrNull() ?: return null
    val isPm = parts[2].uppercase() == "PM"
    val hour24 = when {
        isPm && hour != 12 -> hour + 12
        !isPm && hour == 12 -> 0
        else -> hour
    }
    return hour24 * 3600L + minute * 60L + second
}

/**
 * Computes the wall-clock time at a given point into the dive.
 *
 * @param startSeconds Seconds since midnight at dive start, from [parseStartTimeSeconds].
 * @param elapsedSeconds Seconds elapsed since the start of the dive.
 * @return Pair of (time string `"H:MM"`, am/pm indicator), or `("", "")` if [startSeconds] is null.
 */
fun wallClockTime(startSeconds: Long?, elapsedSeconds: Long): Pair<String, String> {
    if (startSeconds == null) return "" to ""
    val totalSeconds = (startSeconds + elapsedSeconds) % 86400
    val hour24 = (totalSeconds / 3600).toInt()
    val minute = ((totalSeconds % 3600) / 60).toInt()
    val isPm = hour24 >= 12
    val hour12 = when {
        hour24 == 0 -> 12
        hour24 > 12 -> hour24 - 12
        else -> hour24
    }
    val timeStr = "$hour12:${minute.toString().padStart(2, '0')}"
    val amPm = if (isPm) "pm" else "am"
    return timeStr to amPm
}
