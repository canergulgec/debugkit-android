package dev.debugkit.layers.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.get
import dev.debugkit.layers.internal.LayerGeometry
import dev.debugkit.layers.internal.LayerKind
import dev.debugkit.layers.internal.LayerNode
import dev.debugkit.layers.internal.LayerSnapshot
import dev.debugkit.layers.internal.Measurer
import dev.debugkit.layers.internal.SnapshotStore
import dev.debugkit.plugin.DebugPlugin
import dev.debugkit.plugin.DebugSection
import dev.debugkit.ui.DebugKitChip
import dev.debugkit.ui.DebugKitColors
import dev.debugkit.ui.DebugKitEmpty
import dev.debugkit.ui.DebugKitSegments
import dev.debugkit.ui.copyToClipboard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** The Layers screen: the captured app screen, its element tree, and the selected element's properties. */
internal object LayersPlugin : DebugPlugin {

    override val id: String = "debugkit.layers"
    override val title: String = "Layers"
    override val summary: String = "Inspect the screen you opened DebugKit from, in 2D or 3D"
    override val order: Int = 30
    override val section: DebugSection = DebugSection.Test

    @Composable
    override fun Content() {
        val snapshot = SnapshotStore.latest
        if (snapshot == null) {
            DebugKitEmpty(
                "No screen captured yet.\nOpen DebugKit from the screen you want to inspect, by shaking " +
                    "the device or with your app's DebugKit button. That screen is captured at that moment.",
            )
            return
        }
        // A new capture starts with a fresh selection and view.
        key(snapshot) { LayersScreen(snapshot) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayersScreen(snapshot: LayerSnapshot) {
    val byId = remember(snapshot) { snapshot.nodes.associateBy { it.id } }
    var mode by rememberSaveable { mutableStateOf(LayersMode.Select) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    var selectedId by remember { mutableStateOf<Int?>(null) }
    val selected = selectedId?.let(byId::get)

    var measureA by remember { mutableStateOf<Int?>(null) }
    var measureB by remember { mutableStateOf<Int?>(null) }
    val measuredA = measureA?.let(byId::get)
    val measuredB = measureB?.let(byId::get)
    val measurement = remember(measuredA, measuredB) {
        if (measuredA != null && measuredB != null) Measurer.measure(measuredA.bounds, measuredB.bounds) else null
    }

    var colorPoint by remember { mutableStateOf<PixelPoint?>(null) }
    val colorHistory = remember { mutableStateListOf<ColorPick>() }

    var view3D by remember { mutableStateOf(View3D.Default) }
    var flatView by remember { mutableStateOf(FlatView()) }
    val levels = remember(snapshot) { LayerGeometry.visualLevels(snapshot.nodes) }
    val maxLevel = remember(levels) { levels.values.maxOrNull() ?: 0 }
    var levelSlider by remember { mutableStateOf(0f..maxLevel.toFloat()) }
    val levelRange = levelSlider.start.roundToInt()..levelSlider.endInclusive.roundToInt()

    fun elementAt(point: PixelPoint) = LayerGeometry.hitTest(snapshot.nodes, point.x, point.y, 0..Int.MAX_VALUE)

    fun pickAt(point: PixelPoint, tolerance: Int) =
        LayerGeometry.pick(snapshot.nodes, point.x, point.y, tolerance, snapshot.width, snapshot.height)

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = "${snapshot.screen.substringAfterLast('.')} · captured ${TIME.format(Date(snapshot.capturedAt))}",
                style = MaterialTheme.typography.labelLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Legend(LayerKind.View, "${snapshot.count(LayerKind.View)} views")
                Legend(LayerKind.Compose, "${snapshot.count(LayerKind.Compose)} Compose")
                if (snapshot.count(LayerKind.Interop) > 0) Legend(LayerKind.Interop, "${snapshot.count(LayerKind.Interop)} AndroidView")
            }
            if (snapshot.bitmap == null) {
                Text(
                    "The screenshot could not be taken; showing outlines only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DebugKitColors.Error,
                )
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModeChip("Select", LayersMode.Select, mode) { mode = it }
                ModeChip("3D", LayersMode.ThreeD, mode) { mode = it }
                ModeChip("Measure", LayersMode.Measure, mode) { mode = it }
                // Colors come from the screenshot, so there is nothing to pick without one.
                ModeChip("Color", LayersMode.Color, mode, enabled = snapshot.bitmap != null) { mode = it }
                if (mode == LayersMode.ThreeD) TextButton(onClick = { view3D = View3D.Default }) { Text("Reset") }
                if (mode != LayersMode.ThreeD && flatView.isZoomed) TextButton(onClick = { flatView = FlatView() }) { Text("Fit") }
            }
        }

        LayerCanvas(
            snapshot = snapshot,
            mode = mode,
            selected = selected,
            measured = measuredA to measuredB,
            measurement = measurement,
            colorPoint = colorPoint,
            view3D = view3D,
            levels = levels,
            levelRange = levelRange,
            flatView = flatView,
            onTap = { point, tolerance ->
                val hit = pickAt(point, tolerance)
                if (mode == LayersMode.Measure) {
                    // First tap picks A, second picks B, a third starts over with a new A.
                    if (hit == null) return@LayerCanvas
                    if (measureA == null || measureB != null) {
                        measureA = hit.id
                        measureB = null
                    } else if (hit.id != measureA) {
                        measureB = hit.id
                    }
                } else {
                    selectedId = hit?.id
                    if (hit != null) tab = 0
                }
            },
            onColorPoint = { point, released ->
                colorPoint = point
                val bitmap = snapshot.bitmap
                if (released && bitmap != null) {
                    val pick = ColorPick(bitmap[point.x, point.y], elementAt(point)?.displayName())
                    // Each color once: picking it again moves it to the front instead of adding a copy.
                    colorHistory.removeAll { it.color == pick.color }
                    colorHistory.add(0, pick)
                    while (colorHistory.size > MAX_COLOR_HISTORY) colorHistory.removeAt(colorHistory.lastIndex)
                }
            },
            onView3DChange = { view3D = it },
            onFlatViewChange = { flatView = it },
            modifier = Modifier.fillMaxWidth().weight(1f).padding(8.dp),
        )

        if (mode == LayersMode.ThreeD) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                LabeledRow("Spacing") {
                    Slider(
                        value = view3D.spacing,
                        onValueChange = { view3D = View3D(view3D.rotationX, view3D.rotationY, it, view3D.zoom) },
                        valueRange = 0f..80f,
                    )
                }
                if (maxLevel > 0) {
                    LabeledRow("Layers ${levelRange.first}–${levelRange.last}") {
                        RangeSlider(
                            value = levelSlider,
                            onValueChange = { levelSlider = it },
                            valueRange = 0f..maxLevel.toFloat(),
                            steps = (maxLevel - 1).coerceAtLeast(0),
                        )
                    }
                    val shown = snapshot.nodes.count { levels.getValue(it.id) in levelRange }
                    Text(
                        text = "Showing $shown of ${snapshot.nodes.size} elements. Raise the left handle to hide " +
                            "containers, lower the right one to hide inner elements.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DebugKitColors.TextSecondary,
                    )
                }
            }
        }

        when (mode) {
            LayersMode.Measure -> Box(Modifier.fillMaxWidth().weight(0.45f)) {
                MeasurePanel(
                    a = measuredA,
                    b = measuredB,
                    measurement = measurement,
                    density = snapshot.density,
                    onClear = {
                        measureA = null
                        measureB = null
                    },
                )
            }
            LayersMode.Color -> Box(Modifier.fillMaxWidth().weight(0.45f)) {
                val bitmap = snapshot.bitmap
                val point = colorPoint
                val live = if (bitmap != null && point != null) {
                    ColorPick(bitmap[point.x, point.y], elementAt(point)?.displayName())
                } else {
                    null
                }
                ColorPanel(current = live, history = colorHistory)
            }
            LayersMode.Select, LayersMode.ThreeD -> {
                DebugKitSegments(
                    options = listOf("Properties", "Tree"),
                    selected = tab,
                    onSelect = { tab = it },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                )
                Box(Modifier.fillMaxWidth().weight(0.8f)) {
                    if (tab == 0) {
                        Properties(snapshot, selected, onSelect = { selectedId = it.id })
                    } else {
                        Tree(snapshot, selected, onSelect = { selectedId = it.id })
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    value: LayersMode,
    current: LayersMode,
    enabled: Boolean = true,
    onSelect: (LayersMode) -> Unit,
) {
    DebugKitChip(label = label, selected = current == value, onClick = { onSelect(value) }, enabled = enabled)
}

@Composable
private fun Properties(snapshot: LayerSnapshot, node: LayerNode?, onSelect: (LayerNode) -> Unit) {
    val context = LocalContext.current
    if (node == null) {
        DebugKitEmpty("Tap an element on the screen above, or pick one under Tree.")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        // Path from the root, so a small element's containers are one tap away.
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                snapshot.ancestors(node).takeLast(MAX_PATH).forEach { ancestor ->
                    TextButton(onClick = { onSelect(ancestor) }) { Text(ancestor.name, maxLines = 1) }
                    Text("›", color = DebugKitColors.TextSecondary)
                }
                Text(node.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
        items(node.attributes) { (label, value) ->
            ListItem(
                overlineContent = { Text(label) },
                headlineContent = { Text(value) },
                modifier = Modifier.clickable { copyToClipboard(context, label, value) },
            )
        }
    }
}

@Composable
private fun Tree(snapshot: LayerSnapshot, selected: LayerNode?, onSelect: (LayerNode) -> Unit) {
    val state = rememberLazyListState()
    // Bring the selection into view when it was picked on the screen above.
    LaunchedEffect(selected) {
        val index = snapshot.nodes.indexOf(selected)
        if (index >= 0) state.animateScrollToItem((index - 3).coerceAtLeast(0))
    }
    LazyColumn(Modifier.fillMaxSize(), state = state) {
        items(snapshot.nodes, key = { it.id }) { node ->
            val isSelected = node == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isSelected) DebugKitColors.Selected else Color.Transparent)
                    .clickable { onSelect(node) }
                    .padding(start = (8 + (node.depth * INDENT_DP).coerceAtMost(MAX_INDENT_DP)).dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).background(Color(kindColor(node.kind)), CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(node.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                node.label?.let {
                    Text(
                        text = "  $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = DebugKitColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun Legend(kind: LayerKind, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(Color(kindColor(kind)), CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LabeledRow(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(96.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

private const val INDENT_DP = 10
private const val MAX_INDENT_DP = 160
private const val MAX_PATH = 4
private const val MAX_COLOR_HISTORY = 8
private val TIME = SimpleDateFormat("HH:mm:ss", Locale.US)
