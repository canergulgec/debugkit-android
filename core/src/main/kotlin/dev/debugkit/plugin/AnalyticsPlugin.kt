package dev.debugkit.plugin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.debugkit.internal.analytics.AnalyticsEvent
import dev.debugkit.internal.analytics.AnalyticsFormat
import dev.debugkit.internal.analytics.AnalyticsStore
import dev.debugkit.internal.analytics.UserProperty
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

    @OptIn(ExperimentalMaterial3Api::class)
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
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Events") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("User properties") })
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
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Filter by event name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = AnalyticsStore::clear, enabled = events.isNotEmpty()) { Text("Clear") }
            }
            Row(Modifier.padding(horizontal = 8.dp)) {
                TextButton(onClick = { problemsOnly = !problemsOnly }) {
                    Text(if (problemsOnly) "Showing problems only · show all" else "Show problems only")
                }
            }
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
            LazyColumn(Modifier.fillMaxSize()) {
                items(visible, key = { it.id }) { event ->
                    Column(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(event) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = event.name,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (event.duplicate) Tag("SENT TWICE", WARNING)
                            if (event.issues.isNotEmpty()) Tag(if (event.issues.size == 1) "1 ISSUE" else "${event.issues.size} ISSUES", ERROR)
                        }
                        if (event.params.isNotEmpty()) {
                            Text(
                                text = AnalyticsFormat.preview(event.params),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = TIME.format(Date(event.timestamp)) + if (showProvider) "  ·  ${event.provider}" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    @Composable
    private fun EventDetail(event: AnalyticsEvent) {
        val context = LocalContext.current
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(event.name, style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace))
                    Text(
                        "${event.provider} · ${TIME.format(Date(event.timestamp))} · ${event.params.size} parameter${if (event.params.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        TextButton(onClick = { copyToClipboard(context, "Event name", event.name) }) { Text("Copy name") }
                        TextButton(onClick = { copyToClipboard(context, "Parameters", AnalyticsFormat.json(event.params)) }) {
                            Text("Copy as JSON")
                        }
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            item { SectionHeader("Parameters") }
            if (event.params.isEmpty()) item { Placeholder("No parameters.") }
            items(event.params.entries.toList(), key = { it.key }) { (key, value) ->
                ListItem(
                    overlineContent = { Text("$key · ${AnalyticsFormat.type(value)}") },
                    headlineContent = { Text(AnalyticsFormat.value(value), fontFamily = FontFamily.Monospace) },
                    modifier = Modifier.clickable { copyToClipboard(context, key, AnalyticsFormat.value(value)) },
                )
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
        LazyColumn(Modifier.fillMaxSize()) {
            items(properties, key = { "${it.provider}/${it.name}" }) { property: UserProperty ->
                Column(Modifier.fillMaxWidth().clickable { copyToClipboard(context, property.name, property.value.orEmpty()) }) {
                    ListItem(
                        overlineContent = {
                            Text(property.name + (if (showProvider) " · ${property.provider}" else "") + " · set ${TIME.format(Date(property.timestamp))}")
                        },
                        headlineContent = { Text(property.value ?: "(cleared)", fontFamily = FontFamily.Monospace) },
                    )
                    property.issues.forEach { Warning(it, ERROR) }
                }
                HorizontalDivider()
            }
        }
    }

    @Composable
    private fun Tag(text: String, color: Color) {
        Box(Modifier.background(color, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp)) {
            Text(text, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }

    @Composable
    private fun Warning(text: String, color: Color) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Box(Modifier.padding(top = 6.dp).background(color, RoundedCornerShape(50)).padding(4.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
        }
    }

    @Composable
    private fun Placeholder(text: String) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    private val ERROR = Color(0xFFD32F2F)
    private val WARNING = Color(0xFFEF6C00)
    private val TIME = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
}
