package dev.debugkit.network.internal

import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionExportTest {

    @Test
    fun `curl includes method, headers, content type and escaped body`() {
        val tx = HttpTransaction(
            requestedAt = 0,
            method = "POST",
            url = "https://api.example.com/items?q=1",
            requestHeaders = listOf("Authorization" to "••••"),
            requestContentType = "application/json; charset=utf-8",
            requestBody = CapturedBody(text = """{"name":"it's"}""", sizeBytes = 15),
        )
        assertEquals(
            """
            curl -X POST 'https://api.example.com/items?q=1' \
              -H 'Authorization: ••••' \
              -H 'Content-Type: application/json; charset=utf-8' \
              --data-raw '{"name":"it'\''s"}'
            """.trimIndent(),
            TransactionExport.toCurl(tx),
        )
    }

    @Test
    fun `formats byte sizes`() {
        assertEquals("-", TransactionExport.formatBytes(null))
        assertEquals("999 B", TransactionExport.formatBytes(999))
        assertEquals("1.5 kB", TransactionExport.formatBytes(1_500))
        assertEquals("2.0 MB", TransactionExport.formatBytes(2_000_000))
    }
}
