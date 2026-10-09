package dev.debugkit.internal.crash

import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object CrashFormatter {

    /**
     * The human-readable `crash.txt`: when, where, then the full stack trace. Uses
     * printStackTrace rather than Log.getStackTraceString, which hides traces containing
     * UnknownHostException.
     */
    fun format(timestamp: Long, thread: String, throwable: Throwable, details: List<Pair<String, String>>): String =
        buildString {
            appendLine("Crash at ${formatTime(timestamp)}")
            appendLine("Thread: $thread")
            details.forEach { (label, value) -> appendLine("$label: $value") }
            appendLine()
            append(stackTrace(throwable))
        }

    fun stackTrace(throwable: Throwable): String =
        StringWriter().also { writer -> PrintWriter(writer).use { throwable.printStackTrace(it) } }.toString().trimEnd()

    fun formatTime(millis: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(millis))
}
