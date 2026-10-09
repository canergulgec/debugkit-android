package dev.debugkit.network.internal

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkNotificationTest {

    private fun summary(code: Int? = null, error: String? = null, mock: String? = null) = TransactionSummary(
        id = 1,
        requestedAt = 0,
        method = "GET",
        url = "https://api.example.com/posts/1?full=true",
        durationMs = null,
        responseCode = code,
        responseBodySize = null,
        error = error,
        mockRuleName = mock,
    )

    @Test
    fun `line shows status, method and path with query`() {
        assertEquals("200 GET /posts/1?full=true", NetworkNotification.line(summary(code = 200)))
        assertEquals("… GET /posts/1?full=true", NetworkNotification.line(summary()))
        assertEquals("ERR GET /posts/1?full=true", NetworkNotification.line(summary(error = "timeout")))
    }

    @Test
    fun `line flags mocked calls`() {
        assertEquals("[MOCK] 500 GET /posts/1?full=true", NetworkNotification.line(summary(code = 500, mock = "Fail")))
    }
}
