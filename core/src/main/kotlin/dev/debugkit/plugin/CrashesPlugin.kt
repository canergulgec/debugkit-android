package dev.debugkit.plugin

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.debugkit.internal.crash.CrashFormatter
import dev.debugkit.internal.crash.CrashReporter
import dev.debugkit.internal.crash.CrashStore
import dev.debugkit.ui.DebugKitButton
import dev.debugkit.ui.DebugKitCard
import dev.debugkit.ui.DebugKitColors
import dev.debugkit.ui.DebugKitEmpty
import dev.debugkit.ui.DebugKitFonts
import dev.debugkit.ui.DebugKitSegments
import dev.debugkit.ui.DebugKitSubpage
import dev.debugkit.ui.DebugKitTitleButton
import dev.debugkit.ui.copyToClipboard
import dev.debugkit.ui.shareText

/** Recorded crashes, each with the logs and network calls that led up to it. */
internal object CrashesPlugin : DebugPlugin {

    override val id: String = "debugkit.crashes"
    override val title: String = "Crashes"
    override val order: Int = 13
    override val section: DebugSection = DebugSection.Activity

    override val summary: String
        get() {
            CrashReporter.version.intValue // Re-read after changes.
            return when (val count = CrashReporter.store.list().size) {
                0 -> "No crashes recorded"
                1 -> "1 crash recorded"
                else -> "$count crashes recorded"
            }
        }

    @Composable
    override fun Content() {
        val version = CrashReporter.version.intValue
        val crashes = remember(version) { CrashReporter.store.list() }
        var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
        val selected = crashes.firstOrNull { it.id == selectedId }

        BackHandler(enabled = selected != null) { selectedId = null }

        if (selected != null) {
            CrashDetail(selected, onDeleted = { selectedId = null })
        } else {
            CrashList(crashes, onSelect = { selectedId = it.id })
        }
    }

    @Composable
    private fun CrashList(crashes: List<CrashStore.Crash>, onSelect: (CrashStore.Crash) -> Unit) {
        if (crashes.isEmpty()) {
            DebugKitEmpty("No crashes recorded.\nWhen the app crashes, it shows up here with the logs and network calls that led up to it.")
            return
        }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Crashlytics still receives every crash.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DebugKitColors.TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                DebugKitTitleButton("Clear all", onClick = CrashReporter::deleteAll)
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(crashes, key = { it.id }) { crash ->
                    DebugKitCard(Modifier.fillMaxWidth(), onClick = { onSelect(crash) }) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = crash.shortName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = DebugKitColors.Error,
                            )
                            crash.message?.let {
                                Text(it, style = MONO, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Text(
                                text = "${CrashFormatter.formatTime(crash.timestamp)}  ·  ${crash.thread}",
                                style = MaterialTheme.typography.bodySmall,
                                color = DebugKitColors.TextSecondary,
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun CrashDetail(crash: CrashStore.Crash, onDeleted: () -> Unit) {
        val context = LocalContext.current
        // Tab 0 is the crash itself; the rest are its attachments (logs.txt, network.txt, …).
        val files = remember(crash.id) { listOf(CrashStore.CRASH_FILE) + crash.attachments }
        var tab by rememberSaveable(crash.id) { mutableIntStateOf(0) }
        val lines = remember(crash.id, tab) {
            CrashReporter.store.read(crash, files[tab]).orEmpty().lines()
        }
        val highlight = rememberStackHighlighter(context.packageName)
        DebugKitSubpage(crash.shortName)

        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DebugKitButton("Share", onClick = { share(context, crash) }, primary = true)
                DebugKitButton("Copy", onClick = { copyToClipboard(context, "Crash", CrashReporter.store.report(crash)) })
                DebugKitButton(
                    "Delete",
                    onClick = {
                        CrashReporter.delete(crash)
                        onDeleted()
                    },
                )
            }
            if (files.size > 1) {
                DebugKitSegments(
                    options = files.map(::tabTitle),
                    selected = tab,
                    onSelect = { tab = it },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                )
            }
            SelectionContainer {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).background(DebugKitColors.Code, RoundedCornerShape(12.dp)),
                    contentPadding = PaddingValues(12.dp),
                ) {
                    items(lines.size) { index ->
                        Text(
                            text = if (tab == 0) highlight(lines[index]) else AnnotatedString(lines[index]),
                            style = MONO.copy(fontSize = 12.sp, lineHeight = 17.sp),
                        )
                    }
                }
            }
        }
    }

    private val MONO = TextStyle(fontFamily = DebugKitFonts.Mono, fontSize = 13.sp, lineHeight = 18.sp)

    /**
     * Makes the useful lines of a stack trace stand out: "Caused by" lines, and frames from the
     * app's own package, which is usually where the fix goes.
     */
    @Composable
    private fun rememberStackHighlighter(appPackage: String): (String) -> AnnotatedString {
        val error = DebugKitColors.Error
        val own = DebugKitColors.Accent
        val dimmed = DebugKitColors.TextSecondary
        return remember(appPackage, error, own, dimmed) {
            { line ->
                val trimmed = line.trimStart()
                val style = when {
                    trimmed.startsWith("Caused by:") -> SpanStyle(color = error, fontWeight = FontWeight.Bold)
                    trimmed.startsWith("at $appPackage") -> SpanStyle(color = own, fontWeight = FontWeight.Bold)
                    trimmed.startsWith("at ") || trimmed.startsWith("...") -> SpanStyle(color = dimmed)
                    else -> SpanStyle(color = Color.Unspecified)
                }
                buildAnnotatedString { withStyle(style) { append(line) } }
            }
        }
    }

    private fun tabTitle(file: String): String =
        if (file == CrashStore.CRASH_FILE) "Crash" else file.substringBeforeLast('.').replaceFirstChar(Char::uppercase)

    /** Shares the crash and its attachments as one text, for a quick bug report. */
    private fun share(context: Context, crash: CrashStore.Crash) {
        val text = buildString {
            append(CrashReporter.store.report(crash))
            crash.attachments.forEach { file ->
                append("\n\n========== ").append(file).append(" ==========\n")
                append(CrashReporter.store.read(crash, file).orEmpty())
            }
        }
        shareText(context, text, chooserTitle = "Share crash", subject = "Crash: ${crash.shortName}")
    }
}
