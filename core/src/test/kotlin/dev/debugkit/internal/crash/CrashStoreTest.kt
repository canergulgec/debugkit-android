package dev.debugkit.internal.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CrashStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun store() = CrashStore(folder.newFolder("crashes"))

    @Test
    fun `saves a crash with its report and attachments`() {
        val store = store()
        val error = IllegalStateException("Cart is empty")
        store.save(1_000, "main", error, "report text", mapOf("logs.txt" to "a log", "network.txt" to "a call"))

        val crash = store.list().single()
        assertEquals(1_000L, crash.timestamp)
        assertEquals("main", crash.thread)
        assertEquals("java.lang.IllegalStateException", crash.exceptionClass)
        assertEquals("Cart is empty", crash.message)
        assertFalse(crash.seen)
        assertEquals(listOf("logs.txt", "network.txt"), crash.attachments)
        assertEquals("report text", store.report(crash))
        assertEquals("a log", store.read(crash, "logs.txt"))
    }

    @Test
    fun `lists newest first, marks seen, trims and deletes`() {
        val store = store()
        listOf(3_000L, 1_000L, 2_000L).forEach { store.save(it, "main", RuntimeException(), "", emptyMap()) }
        assertEquals(listOf(3_000L, 2_000L, 1_000L), store.list().map { it.timestamp })

        store.markSeen(store.list().first())
        assertTrue(store.list().first().seen)

        store.trim(keep = 2)
        assertEquals(listOf(3_000L, 2_000L), store.list().map { it.timestamp })

        store.delete(store.list().first())
        assertEquals(listOf(2_000L), store.list().map { it.timestamp })

        store.deleteAll()
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun `crash without a message has a null message`() {
        val store = store()
        store.save(1_000, "worker", NullPointerException(), "", emptyMap())
        assertEquals(null, store.list().single().message)
    }

    @Test
    fun `report has details then the full stack trace including causes`() {
        val error = RuntimeException("outer", IllegalArgumentException("inner"))
        val report = CrashFormatter.format(0, "main", error, listOf("App" to "dev.sample 1.0 (1)"))

        val lines = report.lines()
        assertTrue(lines[0].startsWith("Crash at "))
        assertEquals("Thread: main", lines[1])
        assertEquals("App: dev.sample 1.0 (1)", lines[2])
        assertEquals("", lines[3])
        assertEquals("java.lang.RuntimeException: outer", lines[4])
        assertTrue(lines.any { it == "Caused by: java.lang.IllegalArgumentException: inner" })
    }
}
