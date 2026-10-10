package dev.debugkit.internal.push

import org.junit.Assert.assertEquals
import org.junit.Test

class PushDataTest {

    @Test
    fun `parses key=value lines, keeping order and values with equals signs`() {
        val text = """
            type=order
              orderId = 123
            link=myapp://x?a=1&b=2

            not a pair
            =no key
        """.trimIndent()
        assertEquals(
            listOf("type" to "order", "orderId" to "123", "link" to "myapp://x?a=1&b=2"),
            PushData.parse(text).toList(),
        )
    }

    @Test
    fun `format and parse round-trip`() {
        val data = linkedMapOf("type" to "shared_folder", "folderId" to "abc")
        assertEquals(data, PushData.parse(PushData.format(data)))
    }
}
