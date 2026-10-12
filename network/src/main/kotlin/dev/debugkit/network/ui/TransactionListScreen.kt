package dev.debugkit.network.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.debugkit.network.internal.TransactionExport
import dev.debugkit.network.internal.TransactionSummary
import dev.debugkit.network.internal.host
import dev.debugkit.network.internal.pathWithQuery
import dev.debugkit.ui.DebugKitCard
import dev.debugkit.ui.DebugKitColors
import dev.debugkit.ui.DebugKitEmpty
import dev.debugkit.ui.DebugKitFonts
import dev.debugkit.ui.DebugKitSearchField
import dev.debugkit.ui.DebugKitTitleButton


@Composable
internal fun TransactionListScreen(
    transactions: List<TransactionSummary>,
    onSelect: (TransactionSummary) -> Unit,
    onClear: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visible = remember(transactions, query) { transactions.filter { it.matches(query) } }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DebugKitSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Filter by URL, method or status",
                modifier = Modifier.weight(1f),
            )
            DebugKitTitleButton("Clear", enabled = transactions.isNotEmpty(), onClick = onClear)
        }
        NotificationPermissionBanner()

        if (visible.isEmpty()) {
            DebugKitEmpty(if (transactions.isEmpty()) "No requests yet" else "No requests match \"$query\"")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visible, key = { it.id }) { tx ->
                    TransactionRow(tx, onClick = { onSelect(tx) })
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(tx: TransactionSummary, onClick: () -> Unit) {
    DebugKitCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusBadge(tx)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tx.method,
                        color = DebugKitColors.Accent,
                        style = MONO.copy(fontWeight = FontWeight.Medium),
                    )
                    Text(
                        text = tx.pathWithQuery,
                        style = MONO,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (tx.mockRuleName != null) MockTag()
                }
                Text(
                    text = listOfNotNull(
                        tx.host,
                        TransactionExport.formatTime(tx.requestedAt),
                        tx.durationMs?.let { "$it ms" },
                        tx.responseBodySize?.let { TransactionExport.formatBytes(it) },
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = DebugKitColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val MONO = TextStyle(fontFamily = DebugKitFonts.Mono, fontSize = 14.sp)

private fun TransactionSummary.matches(query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim()
    return url.contains(q, ignoreCase = true) ||
        method.equals(q, ignoreCase = true) ||
        responseCode?.toString()?.startsWith(q) == true
}
