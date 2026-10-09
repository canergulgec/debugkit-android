package dev.debugkit.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import dev.debugkit.R
import dev.debugkit.plugin.DebugPlugin

/** Plugin menu plus the selected plugin's screen. Kept dependency-free: no navigation library. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DebugKitApp(
    plugins: List<DebugPlugin>,
    requestedPluginId: String?,
    onPluginRequestHandled: () -> Unit,
    onClose: () -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = plugins.firstOrNull { it.id == selectedId }

    LaunchedEffect(requestedPluginId) {
        if (requestedPluginId != null) {
            selectedId = requestedPluginId
            onPluginRequestHandled()
        }
    }

    BackHandler(enabled = selected != null) { selectedId = null }
    // The back arrow goes through the dispatcher like the system back gesture, so a plugin with
    // its own screens (list -> detail) handles it first.
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selected?.title ?: "DebugKit") },
                navigationIcon = {
                    if (selected != null) {
                        IconButton(onClick = { backDispatcher?.onBackPressed() }) {
                            Icon(painterResource(R.drawable.debugkit_ic_arrow_back), contentDescription = "Back")
                        }
                    } else {
                        IconButton(onClick = onClose) {
                            Icon(painterResource(R.drawable.debugkit_ic_close), contentDescription = "Close")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            if (selected != null) {
                selected.Content()
            } else {
                PluginMenu(plugins = plugins, onSelect = { selectedId = it.id })
            }
        }
    }
}

@Composable
private fun PluginMenu(plugins: List<DebugPlugin>, onSelect: (DebugPlugin) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(plugins, key = { it.id }) { plugin ->
            ListItem(
                headlineContent = { Text(plugin.title) },
                supportingContent = plugin.summary?.let { { Text(it) } },
                modifier = Modifier.clickable { onSelect(plugin) },
            )
            HorizontalDivider()
        }
    }
}
