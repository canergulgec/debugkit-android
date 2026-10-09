package dev.debugkit.network.internal

/** Starting points for the mock rule editor. Unsaved until the user taps Save. */
internal object MockDrafts {

    fun newRule() = MockRule(
        name = "",
        method = null,
        urlPattern = "",
        type = MockType.Replace,
        body = "{\n  \n}",
    )

    /**
     * Prefilled from a recorded call so the user only edits what they want changed. The pattern
     * is the URL without its query, which matches this endpoint whatever the query parameters.
     */
    fun fromTransaction(tx: HttpTransaction) = MockRule(
        name = "${tx.method} ${tx.pathWithQuery.substringBefore('?')}",
        method = tx.method,
        urlPattern = tx.url.substringBefore('?'),
        type = MockType.Replace,
        statusCode = tx.responseCode ?: 200,
        contentType = tx.responseContentType ?: "application/json",
        body = tx.responseBody?.text?.let { JsonFormatter.prettyPrint(it) ?: it }.orEmpty(),
    )
}
