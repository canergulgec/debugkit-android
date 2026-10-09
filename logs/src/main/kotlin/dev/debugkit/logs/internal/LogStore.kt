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

    fun add(entry: LogEntry) {
        val id = nextId.incrementAndGet()
        synchronized(buffer) {
            if (buffer.size == MAX_ENTRIES) buffer.removeFirst()
            buffer.addLast(entry.copy(id = id))
        }
        changes.value = id
    }

    fun snapshot(): List<LogEntry> = synchronized(buffer) { buffer.toList() }

    fun clear() {
        synchronized(buffer) { buffer.clear() }
        changes.value = -nextId.incrementAndGet()
    }
}
