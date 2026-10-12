package dev.debugkit.logs.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import dev.debugkit.logs.internal.LogStore
import dev.debugkit.plugin.DebugPlugin
import dev.debugkit.plugin.DebugSection
import dev.debugkit.plugin.DebugTile
import dev.debugkit.ui.DebugTone
import kotlinx.coroutines.delay
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
        // A tile's state is read by the whole Home screen, and apps can log thousands of lines a
        // second, so the counts refresh at most every REFRESH_INTERVAL_MS, not on every line.
        // Counts only: an unchanged pair does not recompose Home at all.
        val counts by produceState(LogStore.size to LogStore.errorCount) {
            LogStore.version.collect {
                value = LogStore.size to LogStore.errorCount
                delay(REFRESH_INTERVAL_MS)
            }
        }
        val (size, errors) = counts
        return DebugTile(
            label = "Logs",
            value = compact(size),
            note = when (errors) {
                0 -> "no errors"
                1 -> "1 error"
                else -> "$errors errors"
            },
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
