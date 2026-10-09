package dev.debugkit.plugin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.debugkit.internal.DebugSettings
import dev.debugkit.internal.EnvironmentResolver

/** Switches the backend the app talks to. Takes effect after a restart. */
internal object EnvironmentPlugin : DebugPlugin {

    override val id: String = "debugkit.environment"
    override val title: String = "Environment"
    override val order: Int = 20

    override val summary: String
        get() = when (val selected = DebugSettings.selectedEnvironment) {
            null -> "Build default"
            EnvironmentResolver.CUSTOM -> "Custom URL"
            else -> selected
        }

    @Composable
    override fun Content() {
        val selected = DebugSettings.selectedEnvironment

        LazyColumn(Modifier.fillMaxSize()) {
            if (DebugSettings.needsRestart) {
                item { RestartBanner("Restart the app to switch environments.") }
            }
            item {
                Option(
                    title = "Build default",
                    subtitle = "The URL this build was compiled with",
                    selected = selected == null,
                    onSelect = { DebugSettings.selectEnvironment(null) },
                )
            }
            items(DebugSettings.environments, key = { it.name }) { environment ->
                Option(
                    title = environment.name,
                    subtitle = environment.url,
                    monospaceSubtitle = true,
                    selected = selected == environment.name,
                    onSelect = { DebugSettings.selectEnvironment(environment.name) },
                )
            }
            item {
                Option(
                    title = "Custom URL",
                    subtitle = "For example a local or tunnelled server",
                    selected = selected == EnvironmentResolver.CUSTOM,
                    onSelect = { DebugSettings.selectEnvironment(EnvironmentResolver.CUSTOM) },
                )
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
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }

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
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(8.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.let {
                        if (monospaceSubtitle) it.copy(fontFamily = FontFamily.Monospace) else it
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
