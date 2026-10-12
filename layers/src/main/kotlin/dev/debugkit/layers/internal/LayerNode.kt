package dev.debugkit.layers.internal

import android.graphics.Bitmap

internal enum class LayerKind {
    /** A classic View, typically from XML. */
    View,

    /** A Compose node, from the semantics tree. */
    Compose,

    /** A View hosted inside Compose through AndroidView. */
    Interop,
}

/**
 * One element of the captured screen. [bounds] are in the activity window's pixels, the same
 * space as the screenshot, also for elements of dialogs and popups. [parent] is -1 for the root
 * of each window.
 */
internal data class LayerNode(
    val id: Int,
    val parent: Int,
    val depth: Int,
    val kind: LayerKind,
    /** Class name for Views; test tag, role or "Text"/"Layout" for Compose. */
    val name: String,
    /** Short hint shown next to the name, such as a View id or the start of a text. */
    val label: String?,
    val bounds: Bounds,
    val attributes: List<Pair<String, String>>,
    /** 0 for the activity's own window; dialogs, sheets and popups above it count up from 1. */
    val window: Int = 0,
)

/** A rectangle in window pixels. Plain Kotlin rather than android.graphics.Rect, so it works in unit tests. */
internal data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2

    fun contains(x: Int, y: Int): Boolean = x in left until right && y in top until bottom

    fun contains(other: Bounds): Boolean =
        other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom

    val area: Long get() = width.toLong() * height

    /** Straight-line distance from (x, y) to the nearest point of this rectangle; 0 inside it. */
    fun distanceTo(x: Int, y: Int): Double {
        val dx = maxOf(left - x, 0, x - (right - 1))
        val dy = maxOf(top - y, 0, y - (bottom - 1))
        return kotlin.math.sqrt((dx.toLong() * dx + dy.toLong() * dy).toDouble())
    }
}

/** The app screen as it was when DebugKit opened. */
internal class LayerSnapshot(
    val screen: String,
    val capturedAt: Long,
    val density: Float,
    val width: Int,
    val height: Int,
    /** Null when the screenshot could not be taken; the inspector then draws outlines only. */
    val bitmap: Bitmap?,
    /** Pre-order: every node comes right after its parent and before its siblings' subtrees. */
    val nodes: List<LayerNode>,
) {
    fun count(kind: LayerKind): Int = nodes.count { it.kind == kind }

    /** The chain from the root down to [node], root first. */
    fun ancestors(node: LayerNode): List<LayerNode> {
        val byId = nodes.associateBy { it.id }
        return generateSequence(byId[node.parent]) { byId[it.parent] }.toList().asReversed()
    }
}
