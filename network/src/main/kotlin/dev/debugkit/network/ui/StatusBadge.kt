package dev.debugkit.network.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.debugkit.network.internal.TransactionInfo
import dev.debugkit.network.internal.TransactionStatus
import dev.debugkit.network.internal.status

/** Status code on a colored chip. White text on mid-tone fills reads in both light and dark mode. */
@Composable
internal fun StatusBadge(transaction: TransactionInfo, modifier: Modifier = Modifier) {
    val (label, color) = when (transaction.status) {
        TransactionStatus.Pending -> "…" to Gray
        TransactionStatus.Failed -> "ERR" to Red
        TransactionStatus.Complete -> {
            val code = transaction.responseCode ?: 0
            code.toString() to when (code) {
                in 200..299 -> Green
                in 300..399 -> Blue
                in 400..499 -> Orange
                else -> Red
            }
        }
    }
    Box(
        modifier = modifier
            .widthIn(min = 48.dp)
            .background(color, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

/** Marks calls answered by a mock rule. */
@Composable
internal fun MockTag(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Purple, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text("MOCK", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

private val Purple = Color(0xFF7B1FA2)
private val Green = Color(0xFF388E3C)
private val Blue = Color(0xFF1976D2)
private val Orange = Color(0xFFEF6C00)
private val Red = Color(0xFFD32F2F)
private val Gray = Color(0xFF757575)
