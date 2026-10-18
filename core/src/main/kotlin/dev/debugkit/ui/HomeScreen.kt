package dev.debugkit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.debugkit.R
import dev.debugkit.internal.ActionItem
import dev.debugkit.internal.AppRestarter
import dev.debugkit.internal.DebugSettings
import dev.debugkit.internal.EnvironmentResolver
import dev.debugkit.internal.RelativeTime
import dev.debugkit.internal.crash.CrashReporter
import dev.debugkit.internal.isDebuggable
import dev.debugkit.internal.packageInfo
import dev.debugkit.internal.versionLabel
import dev.debugkit.plugin.CrashesPlugin
import dev.debugkit.plugin.DebugPlugin
import dev.debugkit.plugin.DebugTile
import dev.debugkit.plugin.EnvironmentPlugin
import dev.debugkit.plugin.QuickActionsPlugin
import dev.debugkit.plugin.RestartBanner

private const val HOME_ACTIONS = 3

/** "What state is this build in?": environment, live counts, the last crash and quick actions. */
@Composable
internal fun HomeScreen(plugins: List<DebugPlugin>, onOpen: (DebugPlugin) -> Unit, onClose: () -> Unit) {
    // Each plugin's tile is composable, so counts update live while Home is open.
    val tiles = plugins.mapNotNull { plugin -> plugin.tile()?.let { plugin to it } }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header(onClose)
        if (DebugSettings.environments.isNotEmpty()) EnvironmentCard(onChange = { onOpen(EnvironmentPlugin) })
        DebugSettings.actions.filterIsInstance<ActionItem.Choice>().filter { it.pinToHome }.forEach { PinnedChoice(it) }
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (plugin, tile) -> Tile(tile, onClick = { onOpen(plugin) }, modifier = Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        LastCrash(onView = { onOpen(CrashesPlugin) })
        QuickActions(onAll = { onOpen(QuickActionsPlugin) })
    }
}

@Composable
private fun Header(onClose: () -> Unit) {
    val context = LocalContext.current
    val subtitle = remember {
        val label = context.applicationInfo.loadLabel(context.packageManager)
        "$label · ${context.packageInfo().versionLabel}" + if (context.isDebuggable) " · debug" else ""
    }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(DebugKitColors.Accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.debugkit_ic_logo), contentDescription = null, tint = DebugKitColors.OnAccent, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text("DebugKit", style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = DebugKitColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onClose) {
            Icon(painterResource(R.drawable.debugkit_ic_close), contentDescription = "Close DebugKit", tint = DebugKitColors.Text)
        }
    }
}

/** A choice the app pinned to Home: changes apply immediately, like in Quick Actions. */
@Composable
private fun PinnedChoice(item: ActionItem.Choice) {
    var current by remember { mutableStateOf(item.selected()) }
    DebugKitCard {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DebugKitSectionLabel(item.title)
            item.summary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = DebugKitColors.TextSecondary) }
            DebugKitSegments(
                options = item.options,
                selected = item.options.indexOf(current),
                onSelect = { index ->
                    val option = item.options[index]
                    if (option != current) {
                        item.onSelected(option)
                        current = item.selected()
                    }
                },
            )
        }
    }
}

@Composable
private fun EnvironmentCard(onChange: () -> Unit) {
    val selected = DebugSettings.selectedEnvironment
    val (name, url) = when (selected) {
        null -> "Build default" to "The URL this build was compiled with"
        EnvironmentResolver.CUSTOM -> "Custom URL" to DebugSettings.customUrl.ifBlank { "Not set" }
        else -> selected to (DebugSettings.environments.firstOrNull { it.name == selected }?.url ?: "")
    }
    DebugKitCard(onClick = onChange) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                DebugKitSectionLabel("Environment")
                Text(name, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
                Text(url, style = TextStyle(fontFamily = DebugKitFonts.Mono, fontSize = 12.sp), color = DebugKitColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("Change", color = DebugKitColors.Accent, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp))
        }
        if (DebugSettings.needsRestart) RestartStrip()
    }
}

/** Shown inside the environment card when the choice only applies after a restart. */
@Composable
private fun RestartStrip() {
    val context = LocalContext.current
    HorizontalDivider(color = DebugKitColors.Border)
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Restart to apply", style = MaterialTheme.typography.bodySmall, color = DebugKitColors.Warning, modifier = Modifier.weight(1f))
        TextButton(onClick = { AppRestarter.restart(context) }) { Text("Restart", color = DebugKitColors.Warning, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun Tile(tile: DebugTile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    DebugKitCard(modifier = modifier, onClick = onClick) {
        Column(Modifier.padding(14.dp).heightIn(min = 76.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(tile.tone.dot()))
                DebugKitSectionLabel(tile.label, Modifier.padding(start = 8.dp))
            }
            Text(tile.value, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp))
            tile.note?.let { Text(it, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = if (tile.tone == DebugTone.Neutral) DebugKitColors.TextSecondary else tile.tone.color()) }
        }
    }
}

private fun DebugTone.dot(): Color = if (this == DebugTone.Neutral) DebugKitColors.Info else color()

// The last-crash card's red-tinted surface; only used here, so not part of DebugKitColors.
private val CrashCard = Color(0xFF1A1416)
private val CrashCardBorder = Color(0xFF4A2629)
private val CrashCardText = Color(0xFFC9AFB1)
private val CrashCardAction = Color(0xFFFFB3B3)

@Composable
private fun LastCrash(onView: () -> Unit) {
    if (!CrashReporter.isInstalled) return
    val version = CrashReporter.version.intValue
    val crash = remember(version) { CrashReporter.store.list().firstOrNull() } ?: return
    val ago = RelativeTime.format(crash.timestamp, now = System.currentTimeMillis())
    Surface(
        onClick = onView,
        shape = RoundedCornerShape(16.dp),
        color = CrashCard,
        border = BorderStroke(1.dp, CrashCardBorder),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.debugkit_ic_warning), contentDescription = null, tint = DebugKitColors.Error, modifier = Modifier.size(16.dp))
                Text(
                    "CRASHED ${ago.uppercase()}",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, letterSpacing = 0.6.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = DebugKitColors.Error,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(crash.shortName, style = TextStyle(fontFamily = DebugKitFonts.Mono, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DebugKitColors.Text))
            crash.message?.let { Text(it, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = CrashCardText, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            Text("View crash", color = CrashCardAction, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun QuickActions(onAll: () -> Unit) {
    val buttons = DebugSettings.actions.filterIsInstance<ActionItem.Button>()
    if (buttons.isEmpty()) return
    val shown = buttons.take(HOME_ACTIONS)
    // Toggles and values only appear on the full page, so they also call for "All".
    val hasMore = DebugSettings.actions.count { it !is ActionItem.Section } > shown.size
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        DebugKitSectionLabel("Quick actions", Modifier.weight(1f))
        if (hasMore) {
            Text("All", color = DebugKitColors.Accent, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable(role = Role.Button, onClick = onAll).padding(8.dp))
        }
    }
    DebugKitCard {
        shown.forEachIndexed { index, action ->
            if (index > 0) HorizontalDivider(color = DebugKitColors.Border)
            // Summary under the title, as in the App tab: beside it, a long one squeezed the title onto two lines.
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button) { action.onClick() }.padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(action.title, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp))
                    action.summary?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = DebugKitColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Icon(painterResource(R.drawable.debugkit_ic_chevron_right), contentDescription = null, tint = DebugKitColors.TextSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** The App tab: a list of configuration pages, each opening on its own. */
@Composable
internal fun AppScreen(plugins: List<DebugPlugin>, onOpen: (DebugPlugin) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        DebugKitTitle("App")
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (DebugSettings.needsRestart) RestartBanner("Environment changed. Restart to apply.")
            DebugKitCard {
                plugins.forEachIndexed { index, plugin ->
                    if (index > 0) HorizontalDivider(color = DebugKitColors.Border)
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Button) { onOpen(plugin) }.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(plugin.title, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp), fontWeight = FontWeight.Medium)
                            plugin.summary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = DebugKitColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                        Icon(painterResource(R.drawable.debugkit_ic_chevron_right), contentDescription = null, tint = DebugKitColors.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
