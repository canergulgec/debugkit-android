package dev.debugkit.layers.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.debugkit.layers.internal.LayerNode
import dev.debugkit.layers.internal.Measurement
import dev.debugkit.layers.internal.Side
import dev.debugkit.ui.DebugKitColors
import dev.debugkit.ui.DebugKitFonts
import java.util.Locale

/** A color read from the screenshot, and the element it belongs to. */
internal data class ColorPick(val color: Int, val element: String?) {
    /** `#RRGGBB`, or `#AARRGGBB` when not fully opaque. */
    val hex: String
        get() = if (android.graphics.Color.alpha(color) == 0xFF) {
            String.format(Locale.US, "#%06X", color and 0xFFFFFF)
        } else {
            String.format(Locale.US, "#%08X", color)
        }

    val rgb: String
        get() = "rgb(${android.graphics.Color.red(color)}, ${android.graphics.Color.green(color)}, ${android.graphics.Color.blue(color)})"
}

/** Name plus id or text, e.g. `Button · open_debugkit`. */
internal fun LayerNode.displayName(): String = label?.let { "$name · $it" } ?: name

@Composable
internal fun MeasurePanel(
    a: LayerNode?,
    b: LayerNode?,
    measurement: Measurement?,
    density: Float,
    onClear: () -> Unit,
) {
    if (a == null) {
        Hint("Tap two elements on the screen above to measure the distance between them. Pinch to zoom in on small ones.")
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Measure", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onClear) { Text("Clear") }
        }
        ElementLine(MEASURE_A, a, density)
        if (b == null) {
            Text(
                "Now tap a second element.",
                style = MaterialTheme.typography.bodyMedium,
                color = DebugKitColors.TextSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
            return@Column
        }
        ElementLine(MEASURE_B, b, density)
        Spacer(Modifier.size(12.dp))

        val segments = measurement?.segments.orEmpty()
        when {
            measurement?.nested == true -> {
                val inner = if (a.bounds.contains(b.bounds)) "second" else "first"
                Text("The $inner element is inside the other. Its distance to each edge:", style = MaterialTheme.typography.bodyMedium)
                listOf(Side.Left, Side.Top, Side.Right, Side.Bottom).forEach { side ->
                    segments.firstOrNull { it.side == side }?.let { Result(side.name, formatDp(it.length, density)) }
                }
            }
            segments.isEmpty() -> Text("The elements overlap, so there is no gap between them.", style = MaterialTheme.typography.bodyMedium)
            else -> segments.forEach { segment ->
                Result(
                    label = if (segment.side == Side.Horizontal) "Horizontal gap" else "Vertical gap",
                    value = formatDp(segment.length, density),
                )
            }
        }
    }
}

@Composable
private fun ElementLine(color: Int, node: LayerNode, density: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Box(Modifier.size(12.dp).background(Color(color), RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(8.dp))
        Text(
            text = node.displayName(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = "  ${formatDp(node.bounds.width, density).removeSuffix(" dp")} × ${formatDp(node.bounds.height, density)}",
            style = MaterialTheme.typography.bodySmall,
            color = DebugKitColors.TextSecondary,
        )
    }
}

@Composable
private fun Result(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun ColorPanel(current: ColorPick?, history: List<ColorPick>) {
    val context = LocalContext.current
    if (current == null && history.isEmpty()) {
        Hint("Touch the screen above and move your finger to the exact spot; the color under it is shown here. Use two fingers to zoom in.")
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        (current ?: history.first()).let { pick ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { copy(context, "Color", pick.hex) },
            ) {
                Swatch(pick.color, 56)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(pick.hex, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = DebugKitFonts.Mono))
                    Text(pick.rgb, style = MaterialTheme.typography.bodySmall, color = DebugKitColors.TextSecondary)
                    pick.element?.let {
                        Text("on $it", style = MaterialTheme.typography.bodySmall, color = DebugKitColors.TextSecondary, maxLines = 1)
                    }
                }
                TextButton(onClick = { copy(context, "Color", pick.hex) }) { Text("Copy") }
            }
        }
        if (history.isNotEmpty()) {
            Text(
                "Recent · tap to copy",
                style = MaterialTheme.typography.labelMedium,
                color = DebugKitColors.TextSecondary,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                history.forEach { pick ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { copy(context, "Color", pick.hex) },
                    ) {
                        Swatch(pick.color, 36)
                        Text(pick.hex, style = MaterialTheme.typography.labelSmall.copy(fontFamily = DebugKitFonts.Mono))
                    }
                }
            }
        }
    }
}

/** Bordered, so white and near-background colors stay visible. */
@Composable
private fun Swatch(color: Int, sizeDp: Int) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .background(Color(color), RoundedCornerShape(8.dp))
            .border(1.dp, DebugKitColors.Border, RoundedCornerShape(8.dp)),
    )
}

@Composable
private fun Hint(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = DebugKitColors.TextSecondary)
    }
}
