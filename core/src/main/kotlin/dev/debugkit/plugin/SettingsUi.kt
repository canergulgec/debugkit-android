package dev.debugkit.plugin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.debugkit.internal.AppRestarter
import dev.debugkit.ui.DebugKitButton
import dev.debugkit.ui.DebugKitCard
import dev.debugkit.ui.DebugKitColors
import dev.debugkit.ui.DebugKitFonts
import dev.debugkit.ui.DebugKitSectionLabel

@Composable
internal fun SectionHeader(title: String) {
    DebugKitSectionLabel(title, Modifier.padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 8.dp))
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
internal fun RestartBanner(message: String) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DebugKitColors.WarningContainer)
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, style = MaterialTheme.typography.bodySmall, color = DebugKitColors.Warning, modifier = Modifier.weight(1f).padding(end = 8.dp))
        DebugKitButton("Restart app", onClick = { AppRestarter.restart(context) })
    }
}

internal fun copyToClipboard(context: Context, label: String, value: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, value))
    // Android 13+ shows its own clipboard confirmation.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }
}
