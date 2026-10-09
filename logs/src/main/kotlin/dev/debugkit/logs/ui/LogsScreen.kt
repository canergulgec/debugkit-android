package dev.debugkit.logs.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.debugkit.logs.internal.LogEntry
import dev.debugkit.logs.internal.LogFormat
import dev.debugkit.logs.internal.LogStore
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
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search messages and tags") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = LogStore::clear, enabled = entries.isNotEmpty()) { Text("Clear") }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LEVELS.forEach { priority ->
                FilterChip(
                    selected = minPriority == priority,
                    onClick = { minPriority = priority },
                    label = { Text(LogFormat.level(priority) + if (priority > Log.VERBOSE) "+" else "") },
                )
            }
            tag?.let { selectedTag ->
                InputChip(
                    selected = true,
                    onClick = { tag = null },
                    label = { Text("$selectedTag  ✕") },
                )
            }
            TextButton(onClick = { share(context, visible) }, enabled = visible.isNotEmpty()) { Text("Share") }
        }

        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (entries.isEmpty()) "No logs yet" else "No logs match the filters",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(visible, key = { it.id }) { entry ->
                LogRow(
                    entry = entry,
                    expanded = expanded[entry.id] == true,
                    onToggle = { expanded[entry.id] = expanded[entry.id] != true },
                    onTagClick = { tag = entry.tag },
                    onCopy = { copy(context, LogFormat.line(entry)) },
                )
                HorizontalDivider()
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
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LevelBadge(entry.priority)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = LogFormat.time(entry.timestamp),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                entry.tag?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(onClick = onTagClick),
                    )
                }
            }
            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp),
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LevelBadge(priority: Int) {
    Box(
        modifier = Modifier.size(22.dp).background(levelColor(priority), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(LogFormat.level(priority), color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

/** Same mid-tone palette as the Network status badges: white text reads in light and dark mode. */
private fun levelColor(priority: Int): Color = when (priority) {
    Log.VERBOSE -> Color(0xFF757575)
    Log.DEBUG -> Color(0xFF1976D2)
    Log.INFO -> Color(0xFF388E3C)
    Log.WARN -> Color(0xFFEF6C00)
    Log.ERROR -> Color(0xFFD32F2F)
    else -> Color(0xFF7B1FA2)
}

private val LEVELS = listOf(Log.VERBOSE, Log.DEBUG, Log.INFO, Log.WARN, Log.ERROR)

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Log", text))
    // Android 13+ shows its own clipboard confirmation.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Log copied", Toast.LENGTH_SHORT).show()
    }
}

/** Shares the filtered logs oldest first, so they read top to bottom like logcat. */
private fun share(context: Context, newestFirst: List<LogEntry>) {
    val text = newestFirst.take(MAX_SHARED_ENTRIES).asReversed().joinToString("\n", transform = LogFormat::line)
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Share logs"))
}
