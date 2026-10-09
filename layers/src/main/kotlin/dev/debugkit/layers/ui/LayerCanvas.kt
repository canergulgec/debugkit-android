package dev.debugkit.layers.ui

import android.graphics.Camera
import android.graphics.Canvas as NativeCanvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import dev.debugkit.layers.internal.Bounds
import dev.debugkit.layers.internal.LayerGeometry
import dev.debugkit.layers.internal.LayerKind
import dev.debugkit.layers.internal.LayerNode
import dev.debugkit.layers.internal.LayerSnapshot
import kotlin.math.min

/** View state of the 3D mode, owned by the screen so it survives switching tabs. */
internal class View3D(val rotationX: Float, val rotationY: Float, val spacing: Float, val zoom: Float) {
    companion object {
        val Default = View3D(rotationX = 12f, rotationY = -32f, spacing = 22f, zoom = 1f)
    }
}

/**
 * The captured screen. In 2D it is the screenshot with element outlines, and a tap selects the
 * element under the finger. In 3D every element is drawn as its own slice of the screenshot,
 * lifted by its depth (the approach of Jake Wharton's Scalpel); drag rotates, pinch zooms.
 */
@Composable
internal fun LayerCanvas(
    snapshot: LayerSnapshot,
    selected: LayerNode?,
    threeD: Boolean,
    view3D: View3D,
    /** 3D level of each node, by id; see LayerGeometry.visualLevels. */
    levels: Map<Int, Int>,
    /** Levels drawn in 3D. 2D always shows and lets you tap every element. */
    levelRange: IntRange,
    onSelect: (LayerNode?) -> Unit,
    onView3DChange: (View3D) -> Unit,
    modifier: Modifier = Modifier,
) {
    val paints = remember { Paints() }
    val camera = remember { Camera().apply { setLocation(0f, 0f, -16f) } }
    val matrix = remember { Matrix() }
    val visible = remember(snapshot, levels, levelRange) {
        snapshot.nodes.filter { levels.getValue(it.id) in levelRange }
    }

    // Read through updated state: keying pointerInput on them would restart the gesture
    // detector on every frame of a drag and cut the drag short.
    val currentView3D by rememberUpdatedState(view3D)
    val currentOnChange by rememberUpdatedState(onView3DChange)
    val gestures = if (threeD) {
        Modifier.pointerInput(snapshot) {
            detectTransformGestures { _, pan, zoom, _ ->
                val view = currentView3D
                currentOnChange(
                    View3D(
                        rotationX = (view.rotationX - pan.y * 0.25f).coerceIn(-70f, 70f),
                        rotationY = (view.rotationY + pan.x * 0.25f).coerceIn(-70f, 70f),
                        spacing = view.spacing,
                        zoom = (view.zoom * zoom).coerceIn(0.5f, 3f),
                    ),
                )
            }
        }
    } else {
        Modifier.pointerInput(snapshot) {
            detectTapGestures { offset ->
                val fit = Fit(size.width.toFloat(), size.height.toFloat(), snapshot, 1f)
                val x = ((offset.x - fit.left) / fit.scale).toInt()
                val y = ((offset.y - fit.top) / fit.scale).toInt()
                onSelect(LayerGeometry.hitTest(snapshot.nodes, x, y, 0..Int.MAX_VALUE))
            }
        }
    }

    Canvas(modifier.then(gestures)) {
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            if (!threeD) {
                val fit = Fit(size.width, size.height, snapshot, 1f)
                native.save()
                native.translate(fit.left, fit.top)
                native.scale(fit.scale, fit.scale)
                drawScreen(native, snapshot, paints)
                snapshot.nodes.forEach { drawOutline(native, it, paints, fit.scale, faint = true) }
                selected?.let { drawSelection(native, it, paints, fit.scale) }
                native.restore()
                return@drawIntoCanvas
            }

            // Leave room for the layers to fan out when rotated.
            val fit = Fit(size.width, size.height, snapshot, 0.7f * view3D.zoom)
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
        }
    }
}

/** Where the screen sits inside the canvas: scaled to fit, then by [extraScale], centred. */
private class Fit(canvasWidth: Float, canvasHeight: Float, snapshot: LayerSnapshot, extraScale: Float) {
    val scale = min(canvasWidth / snapshot.width, canvasHeight / snapshot.height) * extraScale
    val left = (canvasWidth - snapshot.width * scale) / 2f
    val top = (canvasHeight - snapshot.height * scale) / 2f
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
    val src = Rect()
    val dst = RectF()
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
