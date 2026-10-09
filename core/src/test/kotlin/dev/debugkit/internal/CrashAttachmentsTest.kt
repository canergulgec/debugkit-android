package dev.debugkit.internal

import org.junit.Assert.assertEquals
import org.junit.Test

class CrashAttachmentsTest {

    @Test
    fun `collect skips failing and empty providers`() {
        // The registry is a process-wide singleton; use names no other test registers.
        CrashAttachments.add("t-logs.txt") { "logs" }
        CrashAttachments.add("t-empty.txt") { "  " }
        CrashAttachments.add("t-broken.txt") { error("provider failed") }

        val mine = CrashAttachments.collect().filterKeys { it.startsWith("t-") }
        assertEquals(mapOf("t-logs.txt" to "logs"), mine)
    }
}
