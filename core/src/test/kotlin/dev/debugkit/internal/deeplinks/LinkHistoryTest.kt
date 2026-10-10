package dev.debugkit.internal.deeplinks

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkHistoryTest {

    @Test
    fun `newest first, no repeats, capped`() {
        var history = emptyList<String>()
        listOf("a://1", "a://2", "a://1", "a://3").forEach { history = LinkHistory.add(history, it, max = 2) }
        assertEquals(listOf("a://3", "a://1"), history)
    }
}
