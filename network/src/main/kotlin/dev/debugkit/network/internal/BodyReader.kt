package dev.debugkit.network.internal

import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
import okio.GzipSource
import java.io.EOFException
import java.io.IOException

/**
 * Copies request and response bodies for display without consuming them: the app still reads
 * the full response, because peeking only fills OkHttp's own buffer.
 */
internal object BodyReader {

    fun readRequest(body: RequestBody?, maxBytes: Long): CapturedBody? {
        if (body == null) return null
        if (body.isDuplex() || body.isOneShot()) {
            return CapturedBody("(streamed body, not captured)", body.contentLength())
        }
        val buffer = Buffer()
        return try {
            body.writeTo(buffer)
            capture(buffer, body.contentType(), maxBytes, totalBytes = buffer.size)
        } catch (e: IOException) {
            CapturedBody("(could not read body: $e)", body.contentLength())
        }
    }

    fun readResponse(response: Response, maxBytes: Long): CapturedBody? {
        val body = response.body ?: return null
        if (response.request.method == "HEAD" || response.code == 204 || response.code == 304) return null

        val contentType = body.contentType()
        if (contentType?.subtype == "event-stream") {
            return CapturedBody("(server-sent events, not captured)", -1)
        }

        return try {
            val source = body.source()
            source.request(maxBytes + 1)
            val raw = source.buffer.clone()
            // Only true when the app set Accept-Encoding itself; otherwise OkHttp already unzipped it.
            // Content-Length then counts compressed bytes, so the unzipped size is used instead.
            val gzipped = response.header("Content-Encoding").equals("gzip", ignoreCase = true)
            val buffer = if (gzipped) Buffer().also { out -> GzipSource(raw).use { out.writeAll(it) } } else raw
            val total = body.contentLength().takeIf { it >= 0 && !gzipped } ?: buffer.size
            capture(buffer, contentType, maxBytes, totalBytes = total)
        } catch (e: IOException) {
            CapturedBody("(could not read body: $e)", body.contentLength())
        }
    }

    private fun capture(buffer: Buffer, contentType: MediaType?, maxBytes: Long, totalBytes: Long): CapturedBody {
        if (!isText(contentType, buffer)) return CapturedBody(text = null, sizeBytes = totalBytes)
        val charset = contentType?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
        val byteCount = minOf(buffer.size, maxBytes)
        return CapturedBody(
            text = buffer.readString(byteCount, charset),
            sizeBytes = totalBytes,
            truncated = totalBytes > byteCount,
        )
    }

    private fun isText(contentType: MediaType?, buffer: Buffer): Boolean {
        if (contentType != null) {
            if (contentType.type == "text") return true
            val subtype = contentType.subtype
            if (TEXT_SUBTYPES.any { it in subtype }) return true
            if (contentType.type in BINARY_TYPES) return false
        }
        return buffer.isProbablyUtf8()
    }

    /** Same heuristic as OkHttp's HttpLoggingInterceptor: no control characters in the first code points. */
    private fun Buffer.isProbablyUtf8(): Boolean = try {
        val prefix = Buffer()
        copyTo(prefix, 0, minOf(size, 64))
        (0 until 16).all {
            if (prefix.exhausted()) return@all true
            val codePoint = prefix.readUtf8CodePoint()
            !(Character.isISOControl(codePoint) && !Character.isWhitespace(codePoint))
        }
    } catch (_: EOFException) {
        false
    }

    private val TEXT_SUBTYPES = listOf("json", "xml", "html", "x-www-form-urlencoded", "graphql", "javascript")
    private val BINARY_TYPES = setOf("image", "audio", "video", "font")
}
