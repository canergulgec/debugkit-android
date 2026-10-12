package dev.debugkit.plugin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.debugkit.R
import dev.debugkit.internal.ActionItem
import dev.debugkit.internal.DebugSettings
import dev.debugkit.ui.DebugKitCard
import dev.debugkit.ui.DebugKitColors
import dev.debugkit.ui.copyToClipboard

/** The app's own debug menu items, declared in `DebugKit.install { actions { … } }`. */
internal object QuickActionsPlugin : DebugPlugin {

    override val id: String = "debugkit.actions"
    override val title: String = "Quick Actions"
    override val summary: String = "App-specific tools"
    override val order: Int = 0
    override val section: DebugSection = DebugSection.App

    @Composable
    override fun Content() {
        val context = LocalContext.current
        // Bumped after every action, since a button may change what toggles and values show.
        var generation by remember { mutableIntStateOf(0) }

        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            items(DebugSettings.actions) { item ->
                when (item) {
                    is ActionItem.Section -> SectionHeader(item.title)
                    is ActionItem.Button -> ActionCard(
                        title = item.title,
                        summary = item.summary,
                        onClick = {
                            item.onClick()
                            generation++
                        },
                    ) {
                        Icon(painterResource(R.drawable.debugkit_ic_chevron_right), contentDescription = null, tint = DebugKitColors.TextFaint)
                    }
                    is ActionItem.Toggle -> {
                        var checked by remember(generation) { mutableStateOf(item.isChecked()) }
                        val change: (Boolean) -> Unit = { value ->
                            item.onCheckedChange(value)
                            checked = item.isChecked()
                            generation++
                        }
                        ActionCard(title = item.title, summary = item.summary, onClick = { change(!checked) }) {
                            Switch(checked = checked, onCheckedChange = change)
                        }
                    }
                    is ActionItem.Info -> {
                        val value = remember(generation) {
                            runCatching { item.value() }.getOrElse { "Error: ${it.message}" }
                        }
                        DebugKitCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                            ValueRow(
                                label = item.title,
                                value = value ?: "–",
                                onClick = value?.let { { copyToClipboard(context, item.title, it) } },
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ActionCard(title: String, summary: String?, onClick: () -> Unit, trailing: @Composable () -> Unit) {
        DebugKitCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), onClick = onClick) {
            Row(Modifier.padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    summary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = DebugKitColors.TextSecondary) }
                }
                trailing()
            }
        }
    }
}
