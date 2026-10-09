package dev.debugkit.network.internal

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Fields shared by the full transaction and the lightweight list row. */
internal interface TransactionInfo {
    val url: String
    val responseCode: Int?
    val error: String?
}

internal enum class TransactionStatus { Pending, Complete, Failed }

internal val TransactionInfo.status: TransactionStatus
    get() = when {
        error != null -> TransactionStatus.Failed
        responseCode != null -> TransactionStatus.Complete
        else -> TransactionStatus.Pending
    }

/** Short status for one-line summaries: the code, `ERR` for a failed call, `…` while pending. */
internal val TransactionInfo.statusLabel: String
    get() = when (status) {
        TransactionStatus.Pending -> "…"
        TransactionStatus.Failed -> "ERR"
        TransactionStatus.Complete -> responseCode.toString()
    }

internal val TransactionInfo.host: String
    get() = url.toHttpUrlOrNull()?.host ?: url

/** Path plus query, which is what tells two calls to the same host apart in the list. */
internal val TransactionInfo.pathWithQuery: String
    get() = url.toHttpUrlOrNull()?.let { u -> u.encodedPath + (u.encodedQuery?.let { "?$it" } ?: "") } ?: url

/** One request/response pair, as shown on the detail screen. */
internal data class HttpTransaction(
    val id: Long = 0,
    val requestedAt: Long,
    val method: String,
    override val url: String,
    val requestHeaders: List<Pair<String, String>>,
    val requestContentType: String?,
    val requestBody: CapturedBody?,
    val durationMs: Long? = null,
    val protocol: String? = null,
    override val responseCode: Int? = null,
    val responseMessage: String? = null,
    val responseHeaders: List<Pair<String, String>> = emptyList(),
    val responseContentType: String? = null,
    val responseBody: CapturedBody? = null,
    override val error: String? = null,
    /** Name of the mock rule that produced the response, if one did. */
    val mockRuleName: String? = null,
) : TransactionInfo

/** The columns the list needs. Loaded without headers or bodies so a long list stays cheap. */
internal data class TransactionSummary(
    val id: Long,
    val requestedAt: Long,
    val method: String,
    override val url: String,
    val durationMs: Long?,
    override val responseCode: Int?,
    val responseBodySize: Long?,
    override val error: String?,
    val mockRuleName: String?,
) : TransactionInfo

/**
 * A body as shown in the inspector. [text] is null for binary content. [sizeBytes] is the full
 * size when known, which can be larger than [text] when [truncated].
 */
internal data class CapturedBody(
    val text: String?,
    val sizeBytes: Long,
    val truncated: Boolean = false,
)
