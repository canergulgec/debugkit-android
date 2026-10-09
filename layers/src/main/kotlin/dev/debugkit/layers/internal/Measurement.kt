package dev.debugkit.layers.internal

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** One distance to draw, from (x1, y1) to (x2, y2) in window pixels; always horizontal or vertical. */
internal data class Segment(val side: Side, val x1: Int, val y1: Int, val x2: Int, val y2: Int) {
    val length: Int get() = max(abs(x2 - x1), abs(y2 - y1))
}

internal enum class Side { Left, Top, Right, Bottom, Horizontal, Vertical }

/**
 * The distances between two elements. When one contains the other, [nested] is true and the
 * segments are the inner element's insets on all four sides, zero included; that is how padding
 * is measured. Otherwise they are the horizontal and vertical gaps between the facing edges;
 * none means the elements overlap.
 */
internal data class Measurement(val nested: Boolean, val segments: List<Segment>)

internal object Measurer {

    fun measure(a: Bounds, b: Bounds): Measurement {
        val (inner, outer) = when {
            a.contains(b) -> b to a
            b.contains(a) -> a to b
            else -> return Measurement(nested = false, gaps(a, b))
        }
        val cx = inner.centerX
        val cy = inner.centerY
        return Measurement(
            nested = true,
            segments = listOf(
                Segment(Side.Left, outer.left, cy, inner.left, cy),
                Segment(Side.Top, cx, outer.top, cx, inner.top),
                Segment(Side.Right, inner.right, cy, outer.right, cy),
                Segment(Side.Bottom, cx, inner.bottom, cx, outer.bottom),
            ),
        )
    }

    /**
     * Gap lines sit in the middle of the band the two elements share, like design tools draw
     * them. Elements placed diagonally share no band; the line then runs level with [a].
     */
    private fun gaps(a: Bounds, b: Bounds): List<Segment> = buildList {
        val bandTop = max(a.top, b.top)
        val bandBottom = min(a.bottom, b.bottom)
        val y = if (bandTop < bandBottom) (bandTop + bandBottom) / 2 else a.centerY
        when {
            a.right <= b.left -> add(Segment(Side.Horizontal, a.right, y, b.left, y))
            b.right <= a.left -> add(Segment(Side.Horizontal, b.right, y, a.left, y))
        }

        val bandLeft = max(a.left, b.left)
        val bandRight = min(a.right, b.right)
        val x = if (bandLeft < bandRight) (bandLeft + bandRight) / 2 else a.centerX
        when {
            a.bottom <= b.top -> add(Segment(Side.Vertical, x, a.bottom, x, b.top))
            b.bottom <= a.top -> add(Segment(Side.Vertical, x, b.bottom, x, a.top))
        }
    }
}
