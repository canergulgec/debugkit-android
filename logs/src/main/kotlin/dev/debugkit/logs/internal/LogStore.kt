package dev.debugkit.logs.internal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * The most recent logs, oldest first, in memory only. Apps can log thousands of lines a second,
 * so adding is O(1) under a short lock, and the screen polls a version number instead of
 * receiving a fresh list per line.
 */
internal object LogStore {

    private const val MAX_ENTRIES = 5_000

    private val buffer = ArrayDeque<LogEntry>(MAX_ENTRIES)
    private val nextId = AtomicLong()
    private val changes = MutableStateFlow(0L)

    /** Changes whenever logs are added or cleared. */
    val version: StateFlow<Long> = changes.asStateFlow()

    /** Errors (and asserts) in the buffer, kept as logs come and go so reading it is O(1). */
    @Volatile
    var errorCount: Int = 0
        private set

    val size: Int get() = synchronized(buffer) { buffer.size }

    fun add(entry: LogEntry) {
        val id = nextId.incrementAndGet()
        synchronized(buffer) {
            if (buffer.size == MAX_ENTRIES && buffer.removeFirst().isError) errorCount--
            buffer.addLast(entry.copy(id = id))
            if (entry.isError) errorCount++
        }
        changes.value = id
    }

    private val LogEntry.isError: Boolean get() = priority >= android.util.Log.ERROR

    fun snapshot(): List<LogEntry> = synchronized(buffer) { buffer.toList() }

    fun clear() {
        synchronized(buffer) {
            buffer.clear()
            errorCount = 0
        }
        changes.value = -nextId.incrementAndGet()
    }
}
