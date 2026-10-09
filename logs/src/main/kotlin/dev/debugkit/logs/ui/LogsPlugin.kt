package dev.debugkit.logs.ui

import androidx.compose.runtime.Composable
import dev.debugkit.plugin.DebugPlugin

/** The Logs screen: recent Timber logs with level, tag and text filters. */
internal object LogsPlugin : DebugPlugin {

    override val id: String = "debugkit.logs"
    override val title: String = "Logs"
    override val summary: String = "Recent app logs"
    override val order: Int = 12

    @Composable
    override fun Content() {
        LogsScreen()
    }
}
