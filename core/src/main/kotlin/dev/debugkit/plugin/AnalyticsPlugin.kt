package dev.debugkit.plugin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.debugkit.internal.analytics.AnalyticsEvent
import dev.debugkit.internal.analytics.AnalyticsFormat
import dev.debugkit.internal.analytics.AnalyticsStore
import dev.debugkit.internal.analytics.UserProperty
import dev.debugkit.ui.DebugKitButton
import dev.debugkit.ui.DebugKitCard
import dev.debugkit.ui.DebugKitChip
import dev.debugkit.ui.DebugKitColors
import dev.debugkit.ui.DebugKitEmpty
import dev.debugkit.ui.DebugKitFonts
import dev.debugkit.ui.DebugKitPill
import dev.debugkit.ui.DebugKitSearchField
import dev.debugkit.ui.DebugKitSubpage
import dev.debugkit.ui.DebugKitTitleButton
import dev.debugkit.ui.DebugTone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Events and user properties the app reported with DebugKit.recordAnalyticsEvent / recordUserProperty,
 * flagging the ones Firebase would silently drop and the ones sent twice.
 */
internal object AnalyticsPlugin : DebugPlugin {

    override val id: String = "debugkit.analytics"
    override val title: String = "Analytics"
    override val order: Int = 14
    override val section: DebugSection = DebugSection.Activity

    override val summary: String
        get() {
            val events = AnalyticsStore.events
            val problems = events.count { it.issues.isNotEmpty() || it.duplicate }
            return when {
                events.isEmpty() -> "Events and user properties sent by the app"
                problems > 0 -> "${events.size} events · $problems with problems"
                else -> "${events.size} events"
            }
        }

    @Composable
    override fun tile(): DebugTile? {
        val events = AnalyticsStore.events
        if (events.isEmpty()) return null
        val problems = events.count { it.issues.isNotEmpty() || it.duplicate }
        return DebugTile(
            label = "Analytics",
            value = events.size.toString(),
            note = if (problems == 0) "events, no problems" else "$problems with problems",
            tone = if (problems == 0) DebugTone.Neutral else DebugTone.Warning,
        )
    }

    @Composable
    override fun Content() {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
        val selected = AnalyticsStore.events.firstOrNull { it.id == selectedId }

        BackHandler(enabled = selected != null) { selectedId = null }

        if (selected != null) {
            EventDetail(selected)
            return
        }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DebugKitChip("Events", selected = tab == 0, onClick = { tab = 0 })
                DebugKitChip("User properties", selected = tab == 1, onClick = { tab = 1 })
            }
            if (tab == 0) EventList(onSelect = { selectedId = it.id }) else UserProperties()
        }
    }

    @Composable
    private fun EventList(onSelect: (AnalyticsEvent) -> Unit) {
        var query by rememberSaveable { mutableStateOf("") }
        var problemsOnly by rememberSaveable { mutableStateOf(false) }
        val events = AnalyticsStore.events
        val visible = events.filter { event ->
            (!problemsOnly || event.issues.isNotEmpty() || event.duplicate) &&
                (query.isBlank() || event.name.contains(query.trim(), ignoreCase = true))
        }
        // Several tools at once (Firebase + Adjust, …) show which one each event went to.
        val showProvider = events.map { it.provider }.distinct().size > 1

        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                DebugKitSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Filter by event name",
                    modifier = Modifier.weight(1f),
                )
                DebugKitTitleButton("Clear", enabled = events.isNotEmpty(), onClick = AnalyticsStore::clear)
            }
            DebugKitChip(
                label = "Problems only",
                selected = problemsOnly,
                onClick = { problemsOnly = !problemsOnly },
                modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp),
            )
            if (visible.isEmpty()) {
                Placeholder(
                    when {
                        events.isEmpty() -> "No events yet. They appear here as the app sends them."
                        problemsOnly -> "No problems found in the recorded events."
                        else -> "No events match \"$query\"."
                    },
                )
                return
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visible, key = { it.id }) { event ->
                    DebugKitCard(Modifier.fillMaxWidth(), onClick = { onSelect(event) }) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = event.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = DebugKitFonts.Mono),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (event.duplicate) DebugKitPill("SENT TWICE", DebugTone.Warning, monospace = false)
                                if (event.issues.isNotEmpty()) DebugKitPill(if (event.issues.size == 1) "1 ISSUE" else "${event.issues.size} ISSUES", DebugTone.Error, monospace = false)
                            }
                            if (event.params.isNotEmpty()) {
                                Text(
                                    text = AnalyticsFormat.preview(event.params),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = DebugKitFonts.Mono),
                                    color = DebugKitColors.TextSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                text = TIME.format(Date(event.timestamp)) + if (showProvider) "  ·  ${event.provider}" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = DebugKitColors.TextFaint,
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun EventDetail(event: AnalyticsEvent) {
        val context = LocalContext.current
        DebugKitSubpage("Event")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(event.name, style = MaterialTheme.typography.titleMedium.copy(fontFamily = DebugKitFonts.Mono))
                    Text(
                        "${event.provider} · ${TIME.format(Date(event.timestamp))} · ${event.params.size} parameter${if (event.params.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = DebugKitColors.TextSecondary,
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DebugKitButton("Copy name", onClick = { copyToClipboard(context, "Event name", event.name) })
                        DebugKitButton("Copy as JSON", onClick = { copyToClipboard(context, "Parameters", AnalyticsFormat.json(event.params)) })
                    }
                }
            }
            if (event.duplicate) {
                item { Warning("Sent twice: an event with the same name and parameters was recorded less than a second before. Usually the call fires twice.", WARNING) }
            }
            items(event.issues) { Warning(it, ERROR) }
            if (event.issues.isNotEmpty()) {
                item {
                    Text(
                        "Firebase drops or cuts data that breaks these limits without reporting an error.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DebugKitColors.TextSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            item { SectionHeader("Parameters") }
            if (event.params.isEmpty()) item { Placeholder("No parameters.") }
            if (event.params.isNotEmpty()) {
                item {
                    CardGroup(event.params.entries.toList()) { (key, value) ->
                        ValueRow("$key · ${AnalyticsFormat.type(value)}", AnalyticsFormat.value(value), onClick = { copyToClipboard(context, key, AnalyticsFormat.value(value)) })
                    }
                }
            }
        }
    }

    @Composable
    private fun UserProperties() {
        val context = LocalContext.current
        val properties = AnalyticsStore.userProperties.values.sortedBy { it.name }
        if (properties.isEmpty()) {
            Placeholder("No user properties yet. They appear here when the app records them with DebugKit.recordUserProperty.")
            return
        }
        val showProvider = properties.map { it.provider }.distinct().size > 1
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(properties, key = { "${it.provider}/${it.name}" }) { property: UserProperty ->
                DebugKitCard(Modifier.fillMaxWidth()) {
                    ValueRow(
                        label = property.name + (if (showProvider) " · ${property.provider}" else "") + " · set ${TIME.format(Date(property.timestamp))}",
                        value = property.value ?: "(cleared)",
                        onClick = { copyToClipboard(context, property.name, property.value.orEmpty()) },
                    )
                    if (property.issues.isNotEmpty()) {
                        Column(Modifier.padding(start = 0.dp, bottom = 8.dp)) { property.issues.forEach { Warning(it, ERROR) } }
                    }
                }
            }
        }
    }

    @Composable
    private fun Warning(text: String, color: Color) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Box(Modifier.padding(top = 6.dp).background(color, RoundedCornerShape(50)).padding(4.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.padding(start = 8.dp))
        }
    }

    @Composable
    private fun Placeholder(text: String) {
        DebugKitEmpty(text)
    }

    private val ERROR = DebugKitColors.Error
    private val WARNING = DebugKitColors.Warning
    private val TIME = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
}
