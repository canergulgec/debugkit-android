package dev.debugkit.plugin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.debugkit.internal.DebugSettings
import dev.debugkit.internal.EnvironmentResolver
import dev.debugkit.ui.DebugKitColors
import dev.debugkit.ui.DebugKitFonts

/** Switches the backend the app talks to. Takes effect after a restart. */
internal object EnvironmentPlugin : DebugPlugin {

    override val id: String = "debugkit.environment"
    override val title: String = "Environment"
    override val order: Int = 20
    override val section: DebugSection = DebugSection.App

    override val summary: String
        get() = when (val selected = DebugSettings.selectedEnvironment) {
            null -> "Build default"
            EnvironmentResolver.CUSTOM -> "Custom URL"
            else -> selected
        }

    @Composable
    override fun Content() {
        val selected = DebugSettings.selectedEnvironment

        // Build default, the configured environments, then a custom URL.
        val options = buildList {
            add(EnvOption(null, "Build default", "The URL this build was compiled with", monospace = false))
            DebugSettings.environments.forEach { add(EnvOption(it.name, it.name, it.url, monospace = true)) }
            add(EnvOption(EnvironmentResolver.CUSTOM, "Custom URL", "For example a local or tunnelled server", monospace = false))
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (DebugSettings.needsRestart) {
                item { RestartBanner("Restart the app to switch environments.") }
            }
            item {
                Box(Modifier.padding(top = 8.dp)) {
                    CardGroup(options) { option ->
                        Option(
                            title = option.title,
                            subtitle = option.subtitle,
                            monospaceSubtitle = option.monospace,
                            selected = selected == option.key,
                            onSelect = { DebugSettings.selectEnvironment(option.key) },
                        )
                    }
                }
            }
            if (selected == EnvironmentResolver.CUSTOM) {
                item {
                    OutlinedTextField(
                        value = DebugSettings.customUrl,
                        onValueChange = DebugSettings::updateCustomUrl,
                        label = { Text("Base URL") },
                        placeholder = { Text("https://") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = DebugKitFonts.Mono),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }

    private class EnvOption(val key: String?, val title: String, val subtitle: String, val monospace: Boolean)

    @Composable
    private fun Option(
        title: String,
        subtitle: String,
        selected: Boolean,
        onSelect: () -> Unit,
        monospaceSubtitle: Boolean = false,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(8.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.let {
                        if (monospaceSubtitle) it.copy(fontFamily = DebugKitFonts.Mono) else it
                    },
                    color = DebugKitColors.TextSecondary,
                )
            }
        }
    }
}
