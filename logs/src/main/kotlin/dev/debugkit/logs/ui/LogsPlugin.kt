package dev.debugkit.logs.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.debugkit.logs.internal.LogStore
import dev.debugkit.plugin.DebugPlugin
import dev.debugkit.plugin.DebugSection
import dev.debugkit.plugin.DebugTile
import dev.debugkit.ui.DebugTone
import java.util.Locale

/** The Logs screen: recent Timber logs with level, tag and text filters. */
internal object LogsPlugin : DebugPlugin {

    override val id: String = "debugkit.logs"
    override val title: String = "Logs"
    override val summary: String = "Recent app logs"
    override val order: Int = 12
    override val section: DebugSection = DebugSection.Activity

    @Composable
    override fun tile(): DebugTile {
        // Subscribes to log changes; the counts themselves are kept by the store, so this is cheap.
        val version by LogStore.version.collectAsState()
        val errors = version.let { LogStore.errorCount }
        return DebugTile(
            label = "Logs",
            value = compact(LogStore.size),
            note = if (errors == 0) "no errors" else if (errors == 1) "1 error" else "$errors errors",
            tone = if (errors == 0) DebugTone.Neutral else DebugTone.Error,
        )
    }

    /** 1234 -> "1.2k", like the design's tile. */
    private fun compact(count: Int): String =
        if (count < 1_000) count.toString() else String.format(Locale.US, "%.1fk", count / 1_000.0)

    @Composable
    override fun Content() {
        LogsScreen()
    }
}
