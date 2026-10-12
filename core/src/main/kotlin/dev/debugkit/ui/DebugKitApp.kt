package dev.debugkit.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.debugkit.R
import dev.debugkit.plugin.DebugPlugin
import dev.debugkit.plugin.DebugSection

/** The bottom bar's destinations: Home, then each section that has at least one page. */
internal enum class Tab(val label: String, val icon: Int, val section: DebugSection?) {
    Home("Home", R.drawable.debugkit_ic_home, null),
    Network("Network", R.drawable.debugkit_ic_network, DebugSection.Network),
    Activity("Activity", R.drawable.debugkit_ic_activity, DebugSection.Activity),
    Test("Test", R.drawable.debugkit_ic_test, DebugSection.Test),
    App("App", R.drawable.debugkit_ic_app, DebugSection.App),
}

/**
 * DebugKit's shell: a bottom bar of tabs, a page switcher inside the Network, Activity and Test
 * tabs, and App pages that open on their own. No navigation library, to stay dependency-free.
 */
@Composable
internal fun DebugKitApp(
    plugins: List<DebugPlugin>,
    requestedPluginId: String?,
    onPluginRequestHandled: () -> Unit,
    onClose: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    // The page chosen in each tab's switcher, by section name. Replaced, never mutated, so every
    // change is seen; Kotlin's maps are Serializable, so it survives recreation.
    var pages by rememberSaveable { mutableStateOf<Map<String, String>>(emptyMap()) }
    // A page opened on its own, over the tabs: App pages, or any page Home links to directly.
    var openedId by rememberSaveable { mutableStateOf<String?>(null) }

    val subpage = remember { mutableStateOf<String?>(null) }
    val tabs = Tab.entries.filter { it.section == null || plugins.any { plugin -> plugin.section == it.section } }
    val opened = plugins.firstOrNull { it.id == openedId }

    fun open(plugin: DebugPlugin) {
        if (plugin.section == DebugSection.App) {
            openedId = plugin.id
        } else {
            openedId = null
            tab = Tab.entries.first { it.section == plugin.section }
            pages = pages + (plugin.section.name to plugin.id)
        }
    }

    LaunchedEffect(requestedPluginId) {
        val requested = plugins.firstOrNull { it.id == requestedPluginId } ?: return@LaunchedEffect
        open(requested)
        onPluginRequestHandled()
    }

    // Composed before any page, so a page's own back handling (detail → list) runs first.
    BackHandler(enabled = opened != null) { openedId = null }
    BackHandler(enabled = opened == null && tab != Tab.Home) { tab = Tab.Home }

    CompositionLocalProvider(LocalSubpage provides subpage) {
        // A Surface, not just a background, so text and icons default to light content colors.
        Surface(Modifier.fillMaxSize(), color = DebugKitColors.Background, contentColor = DebugKitColors.Text) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
                    when {
                        opened != null -> OpenedPage(opened)
                        tab == Tab.Home -> HomeScreen(plugins = plugins, onOpen = ::open, onClose = onClose)
                        tab == Tab.App -> AppScreen(plugins = plugins.filter { it.section == DebugSection.App }, onOpen = ::open)
                        else -> {
                            val section = tab.section!!
                            val sectionPlugins = plugins.filter { it.section == section }
                            val selected = sectionPlugins.firstOrNull { it.id == pages[section.name] } ?: sectionPlugins.first()
                            SectionScreen(
                                title = tab.label,
                                subpage = subpage.value,
                                plugins = sectionPlugins,
                                selected = selected,
                                onSelect = { pages = pages + (section.name to it.id) },
                            )
                        }
                    }
                }
                if (opened == null && subpage.value == null) {
                    BottomBar(tabs = tabs, selected = tab, onSelect = { tab = it })
                }
            }
        }
    }
}

/** A tab with several pages: title, page switcher, then the selected page. */
@Composable
private fun SectionScreen(title: String, subpage: String?, plugins: List<DebugPlugin>, selected: DebugPlugin, onSelect: (DebugPlugin) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        if (subpage != null) {
            BackHeader(subpage)
        } else {
            DebugKitTitle(title)
        }
        if (plugins.size > 1 && subpage == null) {
            DebugKitSegments(
                options = plugins.map { it.title },
                selected = plugins.indexOf(selected),
                onSelect = { onSelect(plugins[it]) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
            selected.Content()
        }
    }
}

/** A page opened on its own: a back arrow and its title above it, no bottom bar. */
@Composable
private fun OpenedPage(plugin: DebugPlugin) {
    val subpage = LocalSubpage.current?.value
    Column(Modifier.fillMaxSize()) {
        BackHeader(subpage ?: plugin.title)
        Box(Modifier.weight(1f).fillMaxWidth()) { plugin.Content() }
    }
}

@Composable
private fun BackHeader(title: String) {
    // Goes through the dispatcher like the system back gesture, so the page's own back
    // handling (e.g. editor → list) runs first.
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { dispatcher?.onBackPressed() }) {
            Icon(painterResource(R.drawable.debugkit_ic_arrow_back), contentDescription = "Back", tint = DebugKitColors.Text)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BottomBar(tabs: List<Tab>, selected: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(DebugKitColors.NavBar)) {
        HorizontalDivider(color = DebugKitColors.Divider)
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            tabs.forEach { tab ->
                val isSelected = tab == selected
                val color = if (isSelected) DebugKitColors.Accent else DebugKitColors.TextSecondary
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(selected = isSelected, interactionSource = null, indication = null, role = Role.Tab) { onSelect(tab) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .height(30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(if (isSelected) DebugKitColors.Accent.copy(alpha = 0.15f) else DebugKitColors.NavBar),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(tab.icon), contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        text = tab.label,
                        color = color,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
