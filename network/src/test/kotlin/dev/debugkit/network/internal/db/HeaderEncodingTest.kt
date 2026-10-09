package dev.debugkit.network.internal.db

import org.junit.Assert.assertEquals
import org.junit.Test

class HeaderEncodingTest {

    @Test
    fun `headers round-trip, including colons and spaces in values`() {
        val headers = listOf(
            "Content-Type" to "application/json; charset=utf-8",
            "Date" to "Thu, 08 Oct 2026 19:42:08 GMT",
            "X-Empty" to "",
            "Authorization" to "••••",
        )
        assertEquals(headers, headers.encodeHeaders().decodeHeaders())
    }

    @Test
    fun `no headers round-trip to an empty list`() {
        assertEquals(emptyList<Pair<String, String>>(), emptyList<Pair<String, String>>().encodeHeaders().decodeHeaders())
    }
}
