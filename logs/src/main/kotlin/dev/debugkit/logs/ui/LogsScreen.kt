package dev.debugkit.logs.ui

import android.content.Context
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.debugkit.logs.internal.LogEntry
import dev.debugkit.logs.internal.LogFormat
import dev.debugkit.logs.internal.LogStore
import dev.debugkit.ui.DebugKitChip
import dev.debugkit.ui.DebugKitColors
import dev.debugkit.ui.DebugKitEmpty
import dev.debugkit.ui.DebugKitFonts
import dev.debugkit.ui.DebugKitPill
import dev.debugkit.ui.DebugKitSearchField
import dev.debugkit.ui.DebugKitTitleButton
import dev.debugkit.ui.DebugTone
import dev.debugkit.ui.copyToClipboard
import dev.debugkit.ui.shareText
import kotlinx.coroutines.delay

/** Refresh at most this often while logs pour in, so the list stays readable and cheap. */
private const val REFRESH_INTERVAL_MS = 300L

/** Share intents fail above roughly 1 MB, so sharing is capped to the newest entries. */
private const val MAX_SHARED_ENTRIES = 1_000

@Composable
internal fun LogsScreen() {
    val context = LocalContext.current
    val entries by produceState(initialValue = LogStore.snapshot()) {
        LogStore.version.collect {
            value = LogStore.snapshot()
            delay(REFRESH_INTERVAL_MS)
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var minPriority by rememberSaveable { mutableIntStateOf(Log.VERBOSE) }
    var tag by rememberSaveable { mutableStateOf<String?>(null) }
    val expanded = remember { mutableStateMapOf<Long, Boolean>() }

    // Newest first, like the Network list.
    val visible = remember(entries, query, minPriority, tag) {
        entries.asReversed().filter { LogFormat.matches(it, query, minPriority, tag) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DebugKitSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search messages and tags",
                modifier = Modifier.weight(1f),
            )
            DebugKitTitleButton("Share", enabled = visible.isNotEmpty()) { share(context, visible) }
            DebugKitTitleButton("Clear", enabled = entries.isNotEmpty(), onClick = LogStore::clear)
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LEVELS.forEach { priority ->
                DebugKitChip(
                    label = LogFormat.level(priority) + if (priority > Log.VERBOSE) "+" else "",
                    selected = minPriority == priority,
                    onClick = { minPriority = priority },
                    monospace = true,
                )
            }
            tag?.let { selectedTag ->
                DebugKitChip(label = "$selectedTag  ✕", selected = true, onClick = { tag = null })
            }
        }

        if (visible.isEmpty()) {
            DebugKitEmpty(if (entries.isEmpty()) "No logs yet" else "No logs match the filters")
            return
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(visible, key = { it.id }) { entry ->
                LogRow(
                    entry = entry,
                    expanded = expanded[entry.id] == true,
                    onToggle = { expanded[entry.id] = expanded[entry.id] != true },
                    onTagClick = { tag = entry.tag },
                    onCopy = { copyToClipboard(context, "Log", LogFormat.line(entry)) },
                )
                HorizontalDivider(color = DebugKitColors.Divider, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogRow(
    entry: LogEntry,
    expanded: Boolean,
    onToggle: () -> Unit,
    onTagClick: () -> Unit,
    onCopy: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onToggle, onLongClick = onCopy)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DebugKitPill(LogFormat.level(entry.priority), levelTone(entry.priority), minWidth = 24)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = LogFormat.time(entry.timestamp),
                    style = TextStyle(fontFamily = DebugKitFonts.Mono, fontSize = 11.sp),
                    color = DebugKitColors.TextFaint,
                )
                entry.tag?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = DebugKitColors.Accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(onClick = onTagClick),
                    )
                }
            }
            Text(
                text = entry.message,
                style = TextStyle(fontFamily = DebugKitFonts.Mono, fontSize = 12.sp, lineHeight = 17.sp),
                color = if (entry.isError) DebugKitColors.Error else DebugKitColors.Text,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun levelTone(priority: Int): DebugTone = when (priority) {
    Log.VERBOSE -> DebugTone.Neutral
    Log.DEBUG -> DebugTone.Info
    Log.INFO -> DebugTone.Good
    Log.WARN -> DebugTone.Warning
    Log.ERROR -> DebugTone.Error
    else -> DebugTone.Override
}

private val LEVELS = listOf(Log.VERBOSE, Log.DEBUG, Log.INFO, Log.WARN, Log.ERROR)

/** Shares the filtered logs oldest first, so they read top to bottom like logcat. */
private fun share(context: Context, newestFirst: List<LogEntry>) {
    val text = newestFirst.take(MAX_SHARED_ENTRIES).asReversed().joinToString("\n", transform = LogFormat::line)
    shareText(context, text, chooserTitle = "Share logs")
}
