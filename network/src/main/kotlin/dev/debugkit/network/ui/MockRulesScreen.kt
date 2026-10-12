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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.debugkit.network.internal.MockErrorKind
import dev.debugkit.network.internal.MockRule
import dev.debugkit.network.internal.MockType
import dev.debugkit.ui.DebugKitButton
import dev.debugkit.ui.DebugKitCard
import dev.debugkit.ui.DebugKitColors
import dev.debugkit.ui.DebugKitEmpty
import dev.debugkit.ui.DebugKitFonts
import dev.debugkit.ui.DebugKitPill
import dev.debugkit.ui.DebugTone

@Composable
internal fun MockRulesScreen(
    rules: List<MockRule>,
    onAdd: () -> Unit,
    onEdit: (MockRule) -> Unit,
    onToggle: (MockRule, Boolean) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Checked top to bottom; the first enabled match wins.",
                style = MaterialTheme.typography.bodySmall,
                color = DebugKitColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            DebugKitButton("Add rule", onClick = onAdd, primary = true)
        }

        if (rules.isEmpty()) {
            DebugKitEmpty("No mock rules yet.\nAdd one here, or open a request under Requests and tap \"Mock\".")
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(rules, key = { it.id }) { rule ->
                DebugKitCard(Modifier.fillMaxWidth(), onClick = { onEdit(rule) }) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(rule.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(rule.method ?: "ANY", color = DebugKitColors.Accent, style = MONO.copy(fontWeight = FontWeight.Medium))
                                Text(rule.urlPattern, style = MONO, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                DebugKitPill(outcome(rule), outcomeTone(rule), monospace = false)
                                if (rule.delayMs > 0) DebugKitPill("+${rule.delayMs} ms", DebugTone.Neutral)
                            }
                        }
                        Switch(checked = rule.enabled, onCheckedChange = { onToggle(rule, it) })
                    }
                }
            }
        }
    }
}

private val MONO = TextStyle(fontFamily = DebugKitFonts.Mono, fontSize = 13.sp)

private fun outcomeTone(rule: MockRule): DebugTone = when (rule.type) {
    MockType.Replace -> if (rule.statusCode >= 400) DebugTone.Error else DebugTone.Good
    MockType.NetworkError -> DebugTone.Error
    MockType.DelayOnly -> DebugTone.Warning
}

private fun outcome(rule: MockRule): String = when (rule.type) {
    MockType.Replace -> "Responds ${rule.statusCode}"
    MockType.NetworkError -> when (rule.errorKind) {
        MockErrorKind.Timeout -> "Timeout"
        MockErrorKind.NoConnection -> "No connection"
    }
    MockType.DelayOnly -> "Real response"
}
