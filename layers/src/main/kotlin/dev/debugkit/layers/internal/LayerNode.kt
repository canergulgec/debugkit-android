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
 * One element of the captured screen. [bounds] are in window pixels, the same space as the
 * screenshot. [parent] is -1 for the root.
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
)

/** A rectangle in window pixels. Plain Kotlin rather than android.graphics.Rect, so it works in unit tests. */
internal data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2

    fun contains(x: Int, y: Int): Boolean = x in left until right && y in top until bottom
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
