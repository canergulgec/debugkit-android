package dev.debugkit.plugin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.debugkit.internal.ActionItem
import dev.debugkit.internal.DebugSettings

/** The app's own debug menu items, declared in `DebugKit.install { actions { … } }`. */
internal object QuickActionsPlugin : DebugPlugin {

    override val id: String = "debugkit.actions"
    override val title: String = "Quick Actions"
    override val summary: String = "App-specific tools"
    override val order: Int = 0

    @Composable
    override fun Content() {
        val context = LocalContext.current
        // Bumped after every action, since a button may change what toggles and values show.
        var generation by remember { mutableIntStateOf(0) }

        LazyColumn {
            items(DebugSettings.actions) { item ->
                when (item) {
                    is ActionItem.Section -> SectionHeader(item.title)
                    is ActionItem.Button -> ListItem(
                        headlineContent = { Text(item.title) },
                        supportingContent = item.summary?.let { { Text(it) } },
                        modifier = Modifier.clickable {
                            item.onClick()
                            generation++
                        },
                    )
                    is ActionItem.Toggle -> {
                        var checked by remember(generation) { mutableStateOf(item.isChecked()) }
                        val change: (Boolean) -> Unit = { value ->
                            item.onCheckedChange(value)
                            checked = item.isChecked()
                            generation++
                        }
                        ListItem(
                            headlineContent = { Text(item.title) },
                            supportingContent = item.summary?.let { { Text(it) } },
                            trailingContent = { Switch(checked = checked, onCheckedChange = change) },
                            modifier = Modifier.clickable { change(!checked) },
                        )
                    }
                    is ActionItem.Info -> {
                        val value = remember(generation) {
                            runCatching { item.value() }.getOrElse { "Error: ${it.message}" }
                        }
                        ListItem(
                            overlineContent = { Text(item.title) },
                            headlineContent = { Text(value ?: "–") },
                            modifier = Modifier.clickable(enabled = value != null) {
                                copyToClipboard(context, item.title, value.orEmpty())
                            },
                        )
                    }
                }
            }
        }
    }
}
