package dev.debugkit.plugin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.debugkit.internal.AppRestarter
import dev.debugkit.ui.DebugKitBanner
import dev.debugkit.ui.DebugKitCard
import dev.debugkit.ui.DebugKitColors
import dev.debugkit.ui.DebugKitFonts
import dev.debugkit.ui.DebugKitSectionLabel

/** A group's label, with an optional text action at its end such as "Clear". */
@Composable
internal fun SectionHeader(title: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        DebugKitSectionLabel(title, Modifier.weight(1f))
        if (actionLabel != null) {
            Text(
                text = actionLabel,
                color = DebugKitColors.Accent,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable(onClick = onAction).padding(start = 8.dp),
            )
        }
    }
}

/** Rows grouped on one card, divided by hairlines, as on the design's settings pages. */
@Composable
internal fun <T> CardGroup(rows: List<T>, row: @Composable (T) -> Unit) {
    DebugKitCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        rows.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(color = DebugKitColors.Divider, modifier = Modifier.padding(start = 14.dp))
            row(item)
        }
    }
}

/** A label above a monospace value; tapping copies the value. */
@Composable
internal fun ValueRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = DebugKitColors.TextSecondary)
        Text(value, style = TextStyle(fontFamily = DebugKitFonts.Mono, fontSize = 13.sp, lineHeight = 18.sp))
    }
}

/** Explains that a change needs a restart, with a button that does it. */
@Composable
internal fun RestartBanner(message: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    DebugKitBanner(message, actionLabel = "Restart app", onAction = { AppRestarter.restart(context) }, modifier = modifier)
}
