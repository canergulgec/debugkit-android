package dev.debugkit.network.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.debugkit.network.internal.CapturedBody
import dev.debugkit.network.internal.HttpTransaction
import dev.debugkit.network.internal.JsonColors
import dev.debugkit.network.internal.JsonFormatter
import dev.debugkit.network.internal.TransactionExport
import dev.debugkit.network.internal.TransactionStatus
import dev.debugkit.network.internal.pathWithQuery
import dev.debugkit.network.internal.status

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionDetailScreen(tx: HttpTransaction, onCreateMock: () -> Unit) {
    val context = LocalContext.current
    var tab by rememberSaveable(tx.id) { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Text(
            text = "${tx.method} ${tx.pathWithQuery}",
            style = MaterialTheme.typography.titleSmall,
            maxLines = 3,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
            TextButton(onClick = onCreateMock) { Text("Mock response") }
            TextButton(onClick = { copy(context, "cURL", TransactionExport.toCurl(tx)) }) { Text("Copy cURL") }
            TextButton(onClick = { copy(context, "URL", tx.url) }) { Text("Copy URL") }
            TextButton(onClick = { share(context, tx) }) { Text("Share") }
        }
        PrimaryTabRow(selectedTabIndex = tab) {
            TABS.forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
            }
        }
        when (tab) {
            0 -> OverviewTab(tx)
            1 -> MessageTab(tx.requestHeaders, tx.requestBody)
            else -> MessageTab(tx.responseHeaders, tx.responseBody, pending = tx.status == TransactionStatus.Pending)
        }
    }
}

@Composable
private fun OverviewTab(tx: HttpTransaction) {
    SelectionContainer {
        LazyColumn(Modifier.fillMaxSize()) {
            items(TransactionExport.overview(tx), key = { it.first }) { (label, value) ->
                ListItem(overlineContent = { Text(label) }, headlineContent = { Text(value) })
            }
        }
    }
}

@Composable
private fun MessageTab(headers: List<Pair<String, String>>, body: CapturedBody?, pending: Boolean = false) {
    val formatted = remember(body) {
        body?.text?.let { text ->
            val pretty = JsonFormatter.prettyPrint(text)
            FormattedBody(lines = (pretty ?: text).lines(), isJson = pretty != null)
        }
    }
    val colors = jsonColors()

    SelectionContainer {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            sectionHeader("Headers")
            if (headers.isEmpty()) {
                item { Placeholder(if (pending) "Waiting for response…" else "(none)") }
            }
            items(headers) { (name, value) ->
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(name) }
                        append(": ")
                        append(value)
                    },
                    style = monospaceStyle(),
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }

            sectionHeader(if (body != null) "Body (${TransactionExport.formatBytes(body.sizeBytes)})" else "Body")
            when {
                body == null -> item { Placeholder(if (pending) "Waiting for response…" else "(no body)") }
                formatted == null -> item { Placeholder("(binary content, not shown)") }
                else -> {
                    items(formatted.lines.size) { index ->
                        val line = formatted.lines[index]
                        Text(
                            text = if (formatted.isJson) JsonFormatter.highlight(line, colors) else buildAnnotatedString { append(line) },
                            style = monospaceStyle(),
                        )
                    }
                    if (body.truncated) {
                        item { Placeholder("(truncated: only the first part of the body is shown)") }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private fun LazyListScope.sectionHeader(title: String) = item {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun Placeholder(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun monospaceStyle() = MaterialTheme.typography.bodySmall.copy(
    fontFamily = FontFamily.Monospace,
    fontSize = 12.sp,
    lineHeight = 17.sp,
)

/** GitHub's syntax palette, which keeps contrast on both light and dark surfaces. */
@Composable
private fun jsonColors(): JsonColors {
    val punctuation = MaterialTheme.colorScheme.onSurface
    return if (isSystemInDarkTheme()) {
        JsonColors(Color(0xFFD2A8FF), Color(0xFF7EE787), Color(0xFF79C0FF), Color(0xFFFF7B72), punctuation)
    } else {
        JsonColors(Color(0xFF8250DF), Color(0xFF116329), Color(0xFF0550AE), Color(0xFFCF222E), punctuation)
    }
}

private class FormattedBody(val lines: List<String>, val isJson: Boolean)

private val TABS = listOf("Overview", "Request", "Response")

private fun copy(context: Context, label: String, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
    // Android 13+ shows its own clipboard confirmation.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }
}

private fun share(context: Context, tx: HttpTransaction) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "${tx.method} ${tx.url}")
        .putExtra(Intent.EXTRA_TEXT, TransactionExport.toShareText(tx))
    context.startActivity(Intent.createChooser(send, "Share request"))
}
