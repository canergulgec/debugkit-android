package dev.debugkit.network.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.debugkit.network.internal.TransactionInfo
import dev.debugkit.network.internal.TransactionStatus
import dev.debugkit.network.internal.status
import dev.debugkit.network.internal.statusLabel
import dev.debugkit.ui.DebugKitPill
import dev.debugkit.ui.DebugTone

/** The call's outcome: an HTTP code, ERR for a failed call, or … while it is in flight. */
@Composable
internal fun StatusBadge(transaction: TransactionInfo, modifier: Modifier = Modifier, label: String = transaction.statusLabel) {
    DebugKitPill(label, transaction.tone, modifier, minWidth = 44)
}

internal val TransactionInfo.tone: DebugTone
    get() = when (status) {
        TransactionStatus.Pending -> DebugTone.Neutral
        TransactionStatus.Failed -> DebugTone.Error
        TransactionStatus.Complete -> when (responseCode ?: 0) {
            in 200..299 -> DebugTone.Good
            in 300..399 -> DebugTone.Info
            in 400..499 -> DebugTone.Warning
            else -> DebugTone.Error
        }
    }

/** Marks calls answered by a mock rule. */
@Composable
internal fun MockTag(modifier: Modifier = Modifier) {
    DebugKitPill("MOCK", DebugTone.Override, modifier, monospace = false)
}
