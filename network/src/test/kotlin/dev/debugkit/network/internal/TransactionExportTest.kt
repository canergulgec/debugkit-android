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

    private fun summary(code: Int? = null, error: String? = null, durationMs: Long? = null, mock: String? = null) = TransactionSummary(
        id = 1,
        requestedAt = 0,
        method = "POST",
        url = "https://api.example.com/cart",
        durationMs = durationMs,
        responseCode = code,
        responseBodySize = null,
        error = error,
        mockRuleName = mock,
    )

    @Test
    fun `call line for crash records has status, call, duration and mock`() {
        // The time is in the device's zone, so it is checked through the same formatter.
        val time = TransactionExport.formatTime(0)
        assertEquals(
            "$time 500 POST https://api.example.com/cart (231 ms) [mock: Cart fails]",
            TransactionExport.callLine(summary(code = 500, durationMs = 231, mock = "Cart fails")),
        )
        assertEquals(
            "$time ERR POST https://api.example.com/cart (30000 ms) error: java.net.SocketTimeoutException",
            TransactionExport.callLine(summary(error = "java.net.SocketTimeoutException", durationMs = 30_000)),
        )
        assertEquals("$time … POST https://api.example.com/cart", TransactionExport.callLine(summary()))
    }

    @Test
    fun `formats byte sizes`() {
        assertEquals("-", TransactionExport.formatBytes(null))
        assertEquals("999 B", TransactionExport.formatBytes(999))
        assertEquals("1.5 kB", TransactionExport.formatBytes(1_500))
        assertEquals("2.0 MB", TransactionExport.formatBytes(2_000_000))
    }
}
