package dev.debugkit.network.internal.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.debugkit.network.internal.CapturedBody
import dev.debugkit.network.internal.HttpTransaction

/** Flat storage form of [HttpTransaction]. A null body size means "no body". */
@Entity(tableName = "transactions")
internal data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val requestedAt: Long,
    val method: String,
    val url: String,
    val requestHeaders: String,
    val requestContentType: String?,
    val requestBodyText: String?,
    val requestBodySize: Long?,
    val requestBodyTruncated: Boolean,
    val durationMs: Long?,
    val protocol: String?,
    val responseCode: Int?,
    val responseMessage: String?,
    val responseHeaders: String,
    val responseContentType: String?,
    val responseBodyText: String?,
    val responseBodySize: Long?,
    val responseBodyTruncated: Boolean,
    val error: String?,
    val mockRuleName: String?,
)

internal fun HttpTransaction.toEntity() = TransactionEntity(
    id = id,
    requestedAt = requestedAt,
    method = method,
    url = url,
    requestHeaders = requestHeaders.encodeHeaders(),
    requestContentType = requestContentType,
    requestBodyText = requestBody?.text,
    requestBodySize = requestBody?.sizeBytes,
    requestBodyTruncated = requestBody?.truncated == true,
    durationMs = durationMs,
    protocol = protocol,
    responseCode = responseCode,
    responseMessage = responseMessage,
    responseHeaders = responseHeaders.encodeHeaders(),
    responseContentType = responseContentType,
    responseBodyText = responseBody?.text,
    responseBodySize = responseBody?.sizeBytes,
    responseBodyTruncated = responseBody?.truncated == true,
    error = error,
    mockRuleName = mockRuleName,
)

internal fun TransactionEntity.toModel() = HttpTransaction(
    id = id,
    requestedAt = requestedAt,
    method = method,
    url = url,
    requestHeaders = requestHeaders.decodeHeaders(),
    requestContentType = requestContentType,
    requestBody = requestBodySize?.let { CapturedBody(requestBodyText, it, requestBodyTruncated) },
    durationMs = durationMs,
    protocol = protocol,
    responseCode = responseCode,
    responseMessage = responseMessage,
    responseHeaders = responseHeaders.decodeHeaders(),
    responseContentType = responseContentType,
    responseBody = responseBodySize?.let { CapturedBody(responseBodyText, it, responseBodyTruncated) },
    error = error,
    mockRuleName = mockRuleName,
)

/** One `name: value` per line. HTTP forbids ':' in names and line breaks in values, so this round-trips. */
internal fun List<Pair<String, String>>.encodeHeaders(): String =
    joinToString("\n") { (name, value) -> "$name: $value" }

internal fun String.decodeHeaders(): List<Pair<String, String>> =
    if (isEmpty()) emptyList() else lines().map { it.substringBefore(": ") to it.substringAfter(": ", "") }
