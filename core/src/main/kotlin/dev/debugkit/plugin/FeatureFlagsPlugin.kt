package dev.debugkit.plugin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.debugkit.internal.AppRestarter
import dev.debugkit.internal.DebugSettings
import dev.debugkit.internal.FlagDefinition
import dev.debugkit.ui.DebugKitButton
import dev.debugkit.ui.DebugKitCard
import dev.debugkit.ui.DebugKitChip
import dev.debugkit.ui.DebugKitColors
import dev.debugkit.ui.DebugKitEmpty
import dev.debugkit.ui.DebugKitFonts
import dev.debugkit.ui.DebugKitPill
import dev.debugkit.ui.DebugKitSearchField
import dev.debugkit.ui.DebugTone

/** Overrides feature flag values. Declared flags plus any the app has read so far. */
internal object FeatureFlagsPlugin : DebugPlugin {

    private const val SEARCH_THRESHOLD = 8

    override val id: String = "debugkit.flags"
    override val title: String = "Feature Flags"
    override val order: Int = 21
    override val section: DebugSection = DebugSection.App

    override val summary: String
        get() = when (val count = DebugSettings.overrides.size) {
            0 -> "Override flag values"
            1 -> "1 override active"
            else -> "$count overrides active"
        }

    @Composable
    override fun tile(): DebugTile {
        val count = DebugSettings.overrides.size
        return DebugTile(
            label = "Flags",
            value = count.toString(),
            note = if (count == 0) "none overridden" else "overridden",
            tone = if (count == 0) DebugTone.Neutral else DebugTone.Override,
        )
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val flags = remember { DebugSettings.allFlags() }
        var query by rememberSaveable { mutableStateOf("") }
        val visible = flags.filter {
            query.isBlank() || it.key.contains(query, ignoreCase = true) || it.title.contains(query, ignoreCase = true)
        }

        if (flags.isEmpty()) {
            DebugKitEmpty("No flags yet.\nDeclare them in DebugKit.install { flags { … } }, or read one with DebugKit.flag() and it appears here.")
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Changes apply the next time the app reads a flag. If it only reads them at startup, restart the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DebugKitColors.TextSecondary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DebugKitButton("Restart app", onClick = { AppRestarter.restart(context) })
                        DebugKitButton("Reset all", onClick = DebugSettings::clearAllOverrides, enabled = DebugSettings.overrides.isNotEmpty())
                    }
                    if (flags.size > SEARCH_THRESHOLD) {
                        DebugKitSearchField(value = query, onValueChange = { query = it }, placeholder = "Search flags", modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            items(visible, key = { it.key }) { flag ->
                FlagRow(flag)
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun FlagRow(flag: FlagDefinition) {
        val override = DebugSettings.overrides[flag.key]
        val appValue: Any = DebugSettings.lastDefaults[flag.key] ?: when (flag) {
            is FlagDefinition.BooleanFlag -> flag.default
            is FlagDefinition.ChoiceFlag -> flag.default
            is FlagDefinition.TextFlag -> flag.default
        }

        DebugKitCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(flag.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                            if (override != null) DebugKitPill("OVERRIDE", DebugTone.Override, monospace = false)
                        }
                        if (flag.title != flag.key) {
                            Text(flag.key, style = MaterialTheme.typography.bodySmall.copy(fontFamily = DebugKitFonts.Mono), color = DebugKitColors.TextSecondary)
                        }
                        flag.description?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = DebugKitColors.TextSecondary)
                        }
                        Text(
                            text = if (override != null) {
                                "Using your value. Without it the app would use: ${appValue.toString().ifEmpty { "(empty)" }}"
                            } else {
                                "Using the app's own value"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (override != null) DebugKitColors.Override else DebugKitColors.TextFaint,
                        )
                    }
                    // Boolean flags reset by flipping the switch back, so only the others need a button.
                    if (flag is FlagDefinition.BooleanFlag) {
                        Switch(
                            checked = override as? Boolean ?: appValue as? Boolean ?: flag.default,
                            onCheckedChange = { DebugSettings.applyOverride(flag.key, it, appValue) },
                        )
                    } else if (override != null) {
                        DebugKitButton("Reset", onClick = { DebugSettings.clearOverride(flag.key) })
                    }
                }

                when (flag) {
                    is FlagDefinition.BooleanFlag -> Unit
                    is FlagDefinition.ChoiceFlag -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val current = override as? String ?: appValue as? String ?: flag.default
                        flag.options.forEach { option ->
                            DebugKitChip(
                                label = option,
                                selected = current == option,
                                onClick = { DebugSettings.applyOverride(flag.key, option, appValue) },
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                    }
                    is FlagDefinition.TextFlag -> OutlinedTextField(
                        value = override as? String ?: appValue as? String ?: flag.default,
                        onValueChange = { DebugSettings.applyOverride(flag.key, it, appValue) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = DebugKitFonts.Mono),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
