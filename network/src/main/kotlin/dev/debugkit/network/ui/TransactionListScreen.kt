package dev.debugkit.network.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.debugkit.network.internal.TransactionExport
import dev.debugkit.network.internal.TransactionSummary
import dev.debugkit.network.internal.host
import dev.debugkit.network.internal.pathWithQuery

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
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Filter by URL, method or status") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClear, enabled = transactions.isNotEmpty()) { Text("Clear") }
        }
        NotificationPermissionBanner()

        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (transactions.isEmpty()) "No requests yet" else "No requests match \"$query\"",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(visible, key = { it.id }) { tx ->
                    TransactionRow(tx, onClick = { onSelect(tx) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(tx: TransactionSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusBadge(tx)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (tx.mockRuleName != null) MockTag()
                Text(
                    text = "${tx.method} ${tx.pathWithQuery}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = tx.host,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    TransactionExport.formatTime(tx.requestedAt),
                    tx.durationMs?.let { "$it ms" },
                    tx.responseBodySize?.let { TransactionExport.formatBytes(it) },
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun TransactionSummary.matches(query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim()
    return url.contains(q, ignoreCase = true) ||
        method.equals(q, ignoreCase = true) ||
        responseCode?.toString()?.startsWith(q) == true
}
