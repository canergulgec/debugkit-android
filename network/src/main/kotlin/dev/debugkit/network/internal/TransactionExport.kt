package dev.debugkit.network.internal

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Text forms of a transaction for copying and sharing. */
internal object TransactionExport {

    /**
     * A cURL command that repeats the request. Redacted headers keep their `••••` placeholder,
     * so those need filling in before the command works against an authenticated API.
     */
    fun toCurl(tx: HttpTransaction): String = buildString {
        append("curl -X ").append(tx.method).append(" '").append(tx.url.shellEscaped()).append('\'')
        tx.requestHeaders.forEach { (name, value) ->
            append(" \\\n  -H '").append("$name: $value".shellEscaped()).append('\'')
        }
        // OkHttp adds Content-Type from the body later, so it is usually not among the headers yet.
        if (tx.requestContentType != null && tx.requestHeaders.none { it.first.equals("Content-Type", true) }) {
            append(" \\\n  -H 'Content-Type: ").append(tx.requestContentType.shellEscaped()).append('\'')
        }
        tx.requestBody?.text?.let { body ->
            append(" \\\n  --data-raw '").append(body.shellEscaped()).append('\'')
        }
    }

    /** Everything about the call as plain text, for bug reports. */
    fun toShareText(tx: HttpTransaction): String = buildString {
        appendLine("${tx.method} ${tx.url}")
        overview(tx).forEach { (label, value) -> appendLine("$label: $value") }
        appendLine()
        appendLine("---------- REQUEST ----------")
        tx.requestHeaders.forEach { (name, value) -> appendLine("$name: $value") }
        appendLine()
        appendLine(bodyText(tx.requestBody))
        appendLine()
        appendLine("---------- RESPONSE ----------")
        tx.responseHeaders.forEach { (name, value) -> appendLine("$name: $value") }
        appendLine()
        append(bodyText(tx.responseBody))
    }

    fun overview(tx: HttpTransaction): List<Pair<String, String>> = buildList {
        add("URL" to tx.url)
        add("Method" to tx.method)
        add("Status" to statusText(tx))
        tx.mockRuleName?.let { add("Mocked by" to it) }
        tx.protocol?.let { add("Protocol" to it) }
        add("Requested at" to formatDateTime(tx.requestedAt))
        add("Duration" to (tx.durationMs?.let { "$it ms" } ?: "-"))
        add("Request size" to formatBytes(tx.requestBody?.sizeBytes))
        add("Response size" to formatBytes(tx.responseBody?.sizeBytes))
        tx.error?.let { add("Error" to it) }
    }

    fun statusText(tx: HttpTransaction): String = when (tx.status) {
        TransactionStatus.Pending -> "Pending"
        TransactionStatus.Failed -> "Failed"
        TransactionStatus.Complete -> "${tx.responseCode} ${tx.responseMessage.orEmpty()}".trim()
    }

    fun formatBytes(bytes: Long?): String = when {
        bytes == null || bytes < 0 -> "-"
        bytes < 1_000 -> "$bytes B"
        bytes < 1_000_000 -> String.format(Locale.US, "%.1f kB", bytes / 1_000.0)
        else -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
    }

    fun formatTime(millis: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))

    private fun formatDateTime(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(millis))

    private fun bodyText(body: CapturedBody?): String = when {
        body == null -> "(no body)"
        body.text == null -> "(binary, ${formatBytes(body.sizeBytes)})"
        else -> (JsonFormatter.prettyPrint(body.text) ?: body.text) + if (body.truncated) "\n(truncated)" else ""
    }

    private fun String.shellEscaped() = replace("'", "'\\''")
}
