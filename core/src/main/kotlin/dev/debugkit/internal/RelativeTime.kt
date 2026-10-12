package dev.debugkit.internal

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "5 minutes ago", in English like the rest of DebugKit. Android's DateUtils would follow the
 * device language and mix it into an English screen.
 */
internal object RelativeTime {

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    /** Up to a week back as "… ago"; anything older, or in the future, as a date: "on Oct 3". */
    fun format(millis: Long, now: Long): String {
        val elapsed = now - millis
        return when {
            elapsed < 0 || elapsed >= 7 * DAY -> "on " + SimpleDateFormat("MMM d", Locale.US).format(Date(millis))
            elapsed < MINUTE -> "just now"
            elapsed < HOUR -> ago(elapsed / MINUTE, "minute")
            elapsed < DAY -> ago(elapsed / HOUR, "hour")
            else -> ago(elapsed / DAY, "day")
        }
    }

    private fun ago(count: Long, unit: String): String = "$count $unit${if (count == 1L) "" else "s"} ago"
}
