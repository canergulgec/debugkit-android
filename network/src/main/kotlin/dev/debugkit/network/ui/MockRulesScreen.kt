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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.debugkit.network.internal.MockErrorKind
import dev.debugkit.network.internal.MockRule
import dev.debugkit.network.internal.MockType

@Composable
internal fun MockRulesScreen(
    rules: List<MockRule>,
    onAdd: () -> Unit,
    onEdit: (MockRule) -> Unit,
    onToggle: (MockRule, Boolean) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Checked top to bottom; the first enabled match wins.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onAdd) { Text("Add rule") }
        }

        if (rules.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "No mock rules yet.\nAdd one here, or open a request under Network and tap \"Mock response\".",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(rules, key = { it.id }) { rule ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onEdit(rule) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(rule.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "${rule.method ?: "ANY"}  ${rule.urlPattern}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = describe(rule),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = rule.enabled, onCheckedChange = { onToggle(rule, it) })
                }
                HorizontalDivider()
            }
        }
    }
}

private fun describe(rule: MockRule): String {
    val outcome = when (rule.type) {
        MockType.Replace -> "Responds ${rule.statusCode}"
        MockType.NetworkError -> when (rule.errorKind) {
            MockErrorKind.Timeout -> "Fails with a timeout"
            MockErrorKind.NoConnection -> "Fails with no connection"
        }
        MockType.DelayOnly -> "Real response"
    }
    return if (rule.delayMs > 0) "$outcome after ${rule.delayMs} ms" else outcome
}
