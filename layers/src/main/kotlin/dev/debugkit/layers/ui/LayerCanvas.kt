package dev.debugkit.layers.ui

import android.graphics.Camera
import android.graphics.Canvas as NativeCanvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.debugkit.layers.internal.Bounds
import dev.debugkit.layers.internal.LayerKind
import dev.debugkit.layers.internal.LayerNode
import dev.debugkit.layers.internal.LayerSnapshot
import dev.debugkit.layers.internal.Measurement
import dev.debugkit.layers.internal.Segment
import dev.debugkit.layers.internal.Side
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.min

internal enum class LayersMode { Select, ThreeD, Measure, Color }

/** View state of the 3D mode, owned by the screen so it survives switching tabs. */
internal class View3D(val rotationX: Float, val rotationY: Float, val spacing: Float, val zoom: Float) {
    companion object {
        val Default = View3D(rotationX = 12f, rotationY = -32f, spacing = 22f, zoom = 1f)
    }
}

/**
 * Zoom and pan of the flat modes. [offsetX]/[offsetY] move the screenshot's centre away from the
 * canvas centre, in canvas pixels.
 */
internal data class FlatView(val zoom: Float = 1f, val offsetX: Float = 0f, val offsetY: Float = 0f) {
    val isZoomed: Boolean get() = zoom > 1.01f
}

/** A point on the screenshot, in window pixels. */
internal data class PixelPoint(val x: Int, val y: Int)

/**
 * The captured screen, drawn for the current [mode]:
 * - Select: screenshot with element outlines; a tap reports the window pixel under it, and a
 *   finger-sized tolerance in the same units.
 * - Measure: the same, plus the two measured elements and the distances between them.
 * - Color: the screenshot; pressing and dragging reports the pixel under the finger.
 * The flat modes also pinch to zoom and drag to pan (two fingers in Color, where one picks).
 * - ThreeD: every element as its own slice of the screenshot, lifted by its level (the approach
 *   of Jake Wharton's Scalpel); drag rotates, pinch zooms.
 */
@Composable
internal fun LayerCanvas(
    snapshot: LayerSnapshot,
    mode: LayersMode,
    selected: LayerNode?,
    measured: Pair<LayerNode?, LayerNode?>,
    measurement: Measurement?,
    colorPoint: PixelPoint?,
    view3D: View3D,
    /** 3D level of each node, by id; see LayerGeometry.visualLevels. */
    levels: Map<Int, Int>,
    /** Levels drawn in 3D. The flat modes always show and let you tap every element. */
    levelRange: IntRange,
    flatView: FlatView,
    onTap: (point: PixelPoint, tolerance: Int) -> Unit,
    onColorPoint: (PixelPoint, released: Boolean) -> Unit,
    onView3DChange: (View3D) -> Unit,
    onFlatViewChange: (FlatView) -> Unit,
    modifier: Modifier = Modifier,
) {
    val paints = remember { Paints() }
    val camera = remember { Camera().apply { setLocation(0f, 0f, -16f) } }
    val matrix = remember { Matrix() }
    val visible = remember(snapshot, levels, levelRange) {
        snapshot.nodes.filter { levels.getValue(it.id) in levelRange }
    }
    val density = LocalDensity.current
    val labelTextSize = with(density) { 11.sp.toPx() }
    val labelPadding = with(density) { 4.dp.toPx() }
    val crosshairRadius = with(density) { 14.dp.toPx() }
    val touchTolerance = with(density) { 16.dp.toPx() }

    // Read through updated state: keying pointerInput on them would restart the gesture
    // detector on every frame of a drag and cut the drag short.
    val currentView3D by rememberUpdatedState(view3D)
    val currentOnView3DChange by rememberUpdatedState(onView3DChange)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnColorPoint by rememberUpdatedState(onColorPoint)
    val currentFlatView by rememberUpdatedState(flatView)
    val currentOnFlatViewChange by rememberUpdatedState(onFlatViewChange)

    val gestures = Modifier.pointerInput(snapshot, mode) {
        fun fit() = Fit(size.width.toFloat(), size.height.toFloat(), snapshot, currentFlatView)

        fun toPixel(offset: Offset): PixelPoint {
            val fit = fit()
            return PixelPoint(
                ((offset.x - fit.left) / fit.scale).toInt().coerceIn(0, snapshot.width - 1),
                ((offset.y - fit.top) / fit.scale).toInt().coerceIn(0, snapshot.height - 1),
            )
        }
        when (mode) {
            LayersMode.ThreeD -> detectTransformGestures { _, pan, zoom, _ ->
                val view = currentView3D
                currentOnView3DChange(
                    View3D(
                        rotationX = (view.rotationX - pan.y * 0.25f).coerceIn(-70f, 70f),
                        rotationY = (view.rotationY + pan.x * 0.25f).coerceIn(-70f, 70f),
                        spacing = view.spacing,
                        zoom = (view.zoom * zoom).coerceIn(0.5f, 3f),
                    ),
                )
            }
            // One finger follows the exact pixel, keeping the last one on release; two fingers zoom
            // and pan instead, and that gesture picks nothing.
            LayersMode.Color -> awaitEachGesture {
                val down = awaitFirstDown()
                var picking = true
                var last = down.position
                currentOnColorPoint(toPixel(last), false)
                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.isEmpty()) break
                    if (pressed.size > 1) {
                        picking = false
                        currentOnFlatViewChange(
                            zoomed(currentFlatView, size.width, size.height, snapshot, event.calculateCentroid(), event.calculatePan(), event.calculateZoom()),
                        )
                    } else if (picking) {
                        last = pressed.first().position
                        currentOnColorPoint(toPixel(last), false)
                    }
                    event.changes.forEach { it.consume() }
                }
                if (picking) currentOnColorPoint(toPixel(last), true)
            }
            LayersMode.Select, LayersMode.Measure -> coroutineScope {
                launch {
                    detectTapGestures { currentOnTap(toPixel(it), (touchTolerance / fit().scale).toInt()) }
                }
                launch {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        currentOnFlatViewChange(zoomed(currentFlatView, size.width, size.height, snapshot, centroid, pan, zoom))
                    }
                }
            }
        }
    }

    // Clipped: zoomed in, the screenshot would otherwise draw over the controls around it.
    Canvas(modifier.clipToBounds().then(gestures)) {
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            if (mode == LayersMode.ThreeD) {
                // Leave room for the layers to fan out when rotated.
                val fit = Fit(size.width, size.height, snapshot, FlatView(zoom = 0.7f * view3D.zoom))
                val cx = size.width / 2f
                val cy = size.height / 2f
                visible.forEach { node ->
                    native.save()
                    camera.save()
                    camera.rotateX(view3D.rotationX)
                    camera.rotateY(view3D.rotationY)
                    // Deeper elements come towards the viewer, so children sit on top of parents.
                    camera.translate(0f, 0f, -(levels.getValue(node.id) - levelRange.first) * view3D.spacing)
                    camera.getMatrix(matrix)
                    camera.restore()
                    matrix.preTranslate(-cx, -cy)
                    matrix.postTranslate(cx, cy)
                    native.concat(matrix)
                    native.translate(fit.left, fit.top)
                    native.scale(fit.scale, fit.scale)
                    drawSlice(native, snapshot, node, paints)
                    drawOutline(native, node, paints, fit.scale, faint = false)
                    if (node == selected) drawSelection(native, node, paints, fit.scale)
                    native.restore()
                }
                return@drawIntoCanvas
            }

            val fit = Fit(size.width, size.height, snapshot, flatView)
            native.save()
            native.translate(fit.left, fit.top)
            native.scale(fit.scale, fit.scale)
            drawScreen(native, snapshot, paints)
            if (mode != LayersMode.Color) {
                snapshot.nodes.forEach { drawOutline(native, it, paints, fit.scale, faint = true) }
            }
            when (mode) {
                LayersMode.Select -> selected?.let { drawSelection(native, it, paints, fit.scale) }
                LayersMode.Measure -> {
                    measured.first?.let { drawMeasured(native, it, MEASURE_A, paints, fit.scale) }
                    measured.second?.let { drawMeasured(native, it, MEASURE_B, paints, fit.scale) }
                    measurement?.segments?.forEach { drawSegment(native, it, paints, fit.scale) }
                }
                else -> Unit
            }
            native.restore()

            // Labels and markers are drawn in canvas pixels, so they stay readable at any scale.
            if (mode == LayersMode.Measure) {
                measurement?.segments?.filter { it.length > 0 }?.forEach { segment ->
                    val x = fit.left + (segment.x1 + segment.x2) / 2f * fit.scale
                    val y = fit.top + (segment.y1 + segment.y2) / 2f * fit.scale
                    drawLabel(native, formatDp(segment.length, snapshot.density), x, y, segment.side, paints, labelTextSize, labelPadding)
                }
            }
            if (mode == LayersMode.Color && colorPoint != null) {
                val x = fit.left + (colorPoint.x + 0.5f) * fit.scale
                val y = fit.top + (colorPoint.y + 0.5f) * fit.scale
                drawCrosshair(native, x, y, crosshairRadius, paints)
            }
        }
    }
}

/** Where the screen sits inside the canvas: scaled to fit, then by the view's zoom, centred, then panned. */
private class Fit(canvasWidth: Float, canvasHeight: Float, snapshot: LayerSnapshot, view: FlatView) {
    val scale = min(canvasWidth / snapshot.width, canvasHeight / snapshot.height) * view.zoom
    val left = (canvasWidth - snapshot.width * scale) / 2f + view.offsetX
    val top = (canvasHeight - snapshot.height * scale) / 2f + view.offsetY
}

private const val MAX_ZOOM = 8f

/**
 * Applies one step of a pinch: zooms about the fingers' [centroid], then moves by [pan]. The
 * pan is limited so the screenshot can never be pushed out of view.
 */
private fun zoomed(view: FlatView, width: Int, height: Int, snapshot: LayerSnapshot, centroid: Offset, pan: Offset, zoomChange: Float): FlatView {
    val zoom = (view.zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
    val applied = zoom / view.zoom
    val centerX = width / 2f
    val centerY = height / 2f
    // The screenshot's centre, relative to the fingers, scales with the zoom.
    var offsetX = (view.offsetX + centerX - centroid.x) * applied + centroid.x - centerX + pan.x
    var offsetY = (view.offsetY + centerY - centroid.y) * applied + centroid.y - centerY + pan.y
    val fitted = Fit(width.toFloat(), height.toFloat(), snapshot, FlatView(zoom))
    val maxX = ((snapshot.width * fitted.scale - width) / 2f).coerceAtLeast(0f)
    val maxY = ((snapshot.height * fitted.scale - height) / 2f).coerceAtLeast(0f)
    offsetX = offsetX.coerceIn(-maxX, maxX)
    offsetY = offsetY.coerceIn(-maxY, maxY)
    return FlatView(zoom, offsetX, offsetY)
}

private class Paints {
    val bitmap = Paint(Paint.FILTER_BITMAP_FLAG)
    val placeholder = Paint().apply { color = 0xFFEEEEEE.toInt() }
    val outline = Paint().apply {
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    val selectionFill = Paint().apply { color = 0x40D81B60 }
    val selectionStroke = Paint().apply {
        style = Paint.Style.STROKE
        color = 0xFFD81B60.toInt()
        isAntiAlias = true
    }
    val measured = Paint().apply {
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    val measuredFill = Paint()
    val segment = Paint().apply {
        color = SEGMENT_COLOR
        isAntiAlias = true
    }
    val labelBackground = Paint().apply {
        color = SEGMENT_COLOR
        isAntiAlias = true
    }
    val labelText = Paint().apply {
        color = 0xFFFFFFFF.toInt()
        isAntiAlias = true
        isFakeBoldText = true
    }
    val crosshairOuter = Paint().apply {
        style = Paint.Style.STROKE
        color = 0xFF000000.toInt()
        isAntiAlias = true
    }
    val crosshairInner = Paint().apply {
        style = Paint.Style.STROKE
        color = 0xFFFFFFFF.toInt()
        isAntiAlias = true
    }
    val src = Rect()
    val dst = RectF()
    val label = RectF()
    val textBounds = Rect()
}

private fun drawScreen(canvas: NativeCanvas, snapshot: LayerSnapshot, paints: Paints) {
    val bitmap = snapshot.bitmap
    if (bitmap != null) {
        canvas.drawBitmap(bitmap, 0f, 0f, paints.bitmap)
    } else {
        canvas.drawRect(0f, 0f, snapshot.width.toFloat(), snapshot.height.toFloat(), paints.placeholder)
    }
}

/** The element's own part of the screenshot, or a plain card when there is no screenshot. */
private fun drawSlice(canvas: NativeCanvas, snapshot: LayerSnapshot, node: LayerNode, paints: Paints) {
    val bitmap = snapshot.bitmap
    paints.dst.setFrom(node.bounds)
    if (bitmap == null) {
        canvas.drawRect(paints.dst, paints.placeholder)
        return
    }
    paints.src.set(node.bounds.left, node.bounds.top, node.bounds.right, node.bounds.bottom)
    if (!paints.src.intersect(0, 0, bitmap.width, bitmap.height)) return
    paints.dst.set(paints.src)
    canvas.drawBitmap(bitmap, paints.src, paints.dst, paints.bitmap)
}

/** Stroke widths are divided by [scale] so lines stay the same on screen whatever the zoom. */
private fun drawOutline(canvas: NativeCanvas, node: LayerNode, paints: Paints, scale: Float, faint: Boolean) {
    paints.outline.color = kindColor(node.kind)
    paints.outline.alpha = if (faint) 70 else 255
    paints.outline.strokeWidth = 1.5f / scale
    paints.dst.setFrom(node.bounds)
    canvas.drawRect(paints.dst, paints.outline)
}

private fun drawSelection(canvas: NativeCanvas, node: LayerNode, paints: Paints, scale: Float) {
    paints.dst.setFrom(node.bounds)
    paints.selectionStroke.strokeWidth = 3f / scale
    canvas.drawRect(paints.dst, paints.selectionFill)
    canvas.drawRect(paints.dst, paints.selectionStroke)
}

/** XML blue, Compose green, AndroidView inside Compose orange: shows where a screen is migrated. */
internal fun kindColor(kind: LayerKind): Int = when (kind) {
    LayerKind.View -> 0xFF1976D2.toInt()
    LayerKind.Compose -> 0xFF388E3C.toInt()
    LayerKind.Interop -> 0xFFEF6C00.toInt()
}

/** Fills this rect in place: the draw loop runs per element per frame, so no allocation. */
private fun RectF.setFrom(bounds: Bounds) = set(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat())

/** First and second measured element, distinct from the View/Compose/AndroidView colors. */
internal const val MEASURE_A = 0xFF00897B.toInt()
internal const val MEASURE_B = 0xFF5E35B1.toInt()
private const val SEGMENT_COLOR = 0xFFE53935.toInt()

private fun drawMeasured(canvas: NativeCanvas, node: LayerNode, color: Int, paints: Paints, scale: Float) {
    paints.dst.setFrom(node.bounds)
    paints.measuredFill.color = color
    paints.measuredFill.alpha = 40
    paints.measured.color = color
    paints.measured.strokeWidth = 2.5f / scale
    canvas.drawRect(paints.dst, paints.measuredFill)
    canvas.drawRect(paints.dst, paints.measured)
}

/** A distance line with short end caps, like a ruler. */
private fun drawSegment(canvas: NativeCanvas, segment: Segment, paints: Paints, scale: Float) {
    if (segment.length == 0) return
    paints.segment.strokeWidth = 2f / scale
    val x1 = segment.x1.toFloat()
    val y1 = segment.y1.toFloat()
    val x2 = segment.x2.toFloat()
    val y2 = segment.y2.toFloat()
    canvas.drawLine(x1, y1, x2, y2, paints.segment)
    val cap = 6f / scale
    if (y1 == y2) {
        canvas.drawLine(x1, y1 - cap, x1, y1 + cap, paints.segment)
        canvas.drawLine(x2, y2 - cap, x2, y2 + cap, paints.segment)
    } else {
        canvas.drawLine(x1 - cap, y1, x1 + cap, y1, paints.segment)
        canvas.drawLine(x2 - cap, y2, x2 + cap, y2, paints.segment)
    }
}

/** A pill with the distance, beside the line's middle so it never hides the line. */
private fun drawLabel(
    canvas: NativeCanvas,
    text: String,
    x: Float,
    y: Float,
    side: Side,
    paints: Paints,
    textSize: Float,
    padding: Float,
) {
    paints.labelText.textSize = textSize
    paints.labelText.getTextBounds(text, 0, text.length, paints.textBounds)
    val width = paints.textBounds.width() + padding * 2
    val height = textSize + padding * 2
    val horizontalLine = side == Side.Horizontal || side == Side.Left || side == Side.Right
    val left = if (horizontalLine) x - width / 2 else x + padding
    val top = if (horizontalLine) y - height - padding else y - height / 2
    paints.label.set(left, top, left + width, top + height)
    canvas.drawRoundRect(paints.label, height / 2, height / 2, paints.labelBackground)
    canvas.drawText(text, left + padding, top + padding + textSize * 0.85f, paints.labelText)
}

/** Ring with a gap in the middle, black and white so it shows on any color. */
private fun drawCrosshair(canvas: NativeCanvas, x: Float, y: Float, radius: Float, paints: Paints) {
    paints.crosshairOuter.strokeWidth = radius / 4
    paints.crosshairInner.strokeWidth = radius / 8
    canvas.drawCircle(x, y, radius, paints.crosshairOuter)
    canvas.drawCircle(x, y, radius, paints.crosshairInner)
    canvas.drawLine(x - radius * 1.6f, y, x - radius * 0.4f, y, paints.crosshairOuter)
    canvas.drawLine(x + radius * 0.4f, y, x + radius * 1.6f, y, paints.crosshairOuter)
    canvas.drawLine(x, y - radius * 1.6f, x, y - radius * 0.4f, paints.crosshairOuter)
    canvas.drawLine(x, y + radius * 0.4f, x, y + radius * 1.6f, paints.crosshairOuter)
}

/** Pixels as dp, without a decimal when it is a whole number: "16 dp", "12.5 dp". */
internal fun formatDp(px: Int, density: Float): String {
    val dp = px / density
    val rounded = Math.round(dp * 10) / 10f
    return if (rounded % 1f == 0f) "${rounded.toInt()} dp" else "$rounded dp"
}
