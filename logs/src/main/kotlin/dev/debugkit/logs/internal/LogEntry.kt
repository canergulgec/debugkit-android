package dev.debugkit.logs.internal

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class LogEntry(
    val id: Long = 0,
    val timestamp: Long,
    /** An `android.util.Log` priority constant. */
    val priority: Int,
    val tag: String?,
    val message: String,
    val thread: String,
) {
    /** Errors and asserts: what the Logs tile counts and the list shows in red. */
    val isError: Boolean get() = priority >= Log.ERROR
}

internal object LogFormat {

    fun level(priority: Int): String = when (priority) {
        Log.VERBOSE -> "V"
        Log.DEBUG -> "D"
        Log.INFO -> "I"
        Log.WARN -> "W"
        Log.ERROR -> "E"
        Log.ASSERT -> "A"
        else -> "?"
    }

    fun time(millis: Long): String = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(millis))

    /** Logcat-style line, e.g. `2026-10-09 10:15:02.481 D/LoginViewModel: Signed in`. */
    fun line(entry: LogEntry): String {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(entry.timestamp))
        return "$date ${level(entry.priority)}/${entry.tag.orEmpty()}: ${entry.message}"
    }

    /**
     * Whether [entry] passes the Logs screen's filters: at least [minPriority], from [tag] when set,
     * and containing [query] in its message or tag.
     */
    fun matches(entry: LogEntry, query: String, minPriority: Int, tag: String?): Boolean {
        if (entry.priority < minPriority) return false
        if (tag != null && entry.tag != tag) return false
        if (query.isBlank()) return true
        val q = query.trim()
        return entry.message.contains(q, ignoreCase = true) || entry.tag?.contains(q, ignoreCase = true) == true
    }
}
