package dev.debugkit.network.internal

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Produces the outcome a [MockRule] asks for, in place of (or before) the real call. */
internal object MockResponder {

    /** Header added to every mocked response so the app's own logs can tell them apart too. */
    const val MOCK_HEADER = "X-DebugKit-Mock"

    fun respond(rule: MockRule, chain: Interceptor.Chain): Response {
        if (rule.delayMs > 0) sleep(rule.delayMs)
        val request = chain.request()

        return when (rule.type) {
            MockType.Replace -> {
                val now = System.currentTimeMillis()
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(rule.statusCode)
                    .message(reasonPhrase(rule.statusCode))
                    .header("Content-Type", rule.contentType)
                    .header(MOCK_HEADER, rule.name)
                    .body(rule.body.toResponseBody(rule.contentType.toMediaTypeOrNull()))
                    .sentRequestAtMillis(now)
                    .receivedResponseAtMillis(now)
                    .build()
            }
            MockType.NetworkError -> throw when (rule.errorKind) {
                MockErrorKind.Timeout -> SocketTimeoutException("DebugKit mock \"${rule.name}\": timeout")
                MockErrorKind.NoConnection -> UnknownHostException("DebugKit mock \"${rule.name}\": no connection")
            }
            MockType.DelayOnly -> chain.proceed(request)
        }
    }

    private fun sleep(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("DebugKit mock delay interrupted").apply { initCause(e) }
        }
    }

    private fun reasonPhrase(code: Int): String = when (code) {
        200 -> "OK"
        201 -> "Created"
        204 -> "No Content"
        301 -> "Moved Permanently"
        302 -> "Found"
        304 -> "Not Modified"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        409 -> "Conflict"
        422 -> "Unprocessable Entity"
        429 -> "Too Many Requests"
        500 -> "Internal Server Error"
        502 -> "Bad Gateway"
        503 -> "Service Unavailable"
        504 -> "Gateway Timeout"
        else -> "Mocked"
    }
}
