package dev.debugkit.logs.internal

import android.util.Log
import dev.debugkit.logs.DebugKitTree
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber

class DebugKitTreeTest {

    @Before
    fun setUp() {
        LogStore.clear()
    }

    @After
    fun tearDown() {
        Timber.uprootAll()
        LogStore.clear()
    }

    @Test
    fun `records priority, explicit tag and message`() {
        Timber.plant(DebugKitTree())
        Timber.tag("Checkout").w("Payment slow: %d ms", 1200)

        val entry = LogStore.snapshot().single()
        assertEquals(Log.WARN, entry.priority)
        assertEquals("Checkout", entry.tag)
        assertEquals("Payment slow: 1200 ms", entry.message)
    }

    @Test
    fun `without a tag uses the calling class name`() {
        Timber.plant(DebugKitTree())
        Timber.d("hello")

        assertEquals("DebugKitTreeTest", LogStore.snapshot().single().tag)
    }

    @Test
    fun `throwable stack trace is part of the message`() {
        Timber.plant(DebugKitTree())
        Timber.e(IllegalStateException("boom"), "Sync failed")

        val message = LogStore.snapshot().single().message
        assertTrue(message.startsWith("Sync failed\n"))
        assertTrue("java.lang.IllegalStateException: boom" in message)
    }

    @Test
    fun `logs below minPriority are skipped`() {
        Timber.plant(DebugKitTree { minPriority = Log.INFO })
        Timber.d("dropped")
        Timber.i("kept")

        assertEquals(listOf("kept"), LogStore.snapshot().map { it.message })
    }

    @Test
    fun `filter matches level, tag and text`() {
        val entry = LogEntry(timestamp = 0, priority = Log.WARN, tag = "Net", message = "Timeout on /cart", thread = "main")
        assertTrue(LogFormat.matches(entry, "", Log.VERBOSE, null))
        assertTrue(LogFormat.matches(entry, "cart", Log.WARN, "Net"))
        assertTrue(LogFormat.matches(entry, "net", Log.VERBOSE, null))
        assertEquals(false, LogFormat.matches(entry, "", Log.ERROR, null))
        assertEquals(false, LogFormat.matches(entry, "", Log.VERBOSE, "Ui"))
        assertEquals(false, LogFormat.matches(entry, "login", Log.VERBOSE, null))
    }
}
