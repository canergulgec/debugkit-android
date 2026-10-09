package dev.debugkit.layers.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurerTest {

    @Test
    fun `side by side elements give the horizontal gap in their shared band`() {
        val left = Bounds(0, 0, 100, 50)
        val right = Bounds(140, 10, 200, 70)
        val m = Measurer.measure(left, right)
        assertFalse(m.nested)
        assertEquals(listOf(Segment(Side.Horizontal, 100, 30, 140, 30)), m.segments)
        assertEquals(40, m.segments.single().length)
        // Order of the two taps does not matter.
        assertEquals(m.segments, Measurer.measure(right, left).segments)
    }

    @Test
    fun `stacked elements give the vertical gap`() {
        val title = Bounds(20, 0, 220, 40)
        val button = Bounds(40, 56, 140, 100)
        assertEquals(listOf(Segment(Side.Vertical, 90, 40, 90, 56)), Measurer.measure(title, button).segments)
    }

    @Test
    fun `diagonal elements give both gaps`() {
        val m = Measurer.measure(Bounds(0, 0, 10, 10), Bounds(30, 50, 40, 60))
        assertEquals(listOf(Side.Horizontal, Side.Vertical), m.segments.map { it.side })
        assertEquals(listOf(20, 40), m.segments.map { it.length })
    }

    @Test
    fun `touching elements measure zero`() {
        assertEquals(0, Measurer.measure(Bounds(0, 0, 10, 10), Bounds(10, 0, 20, 10)).segments.single().length)
    }

    @Test
    fun `overlapping elements have no gaps`() {
        val m = Measurer.measure(Bounds(0, 0, 50, 50), Bounds(25, 25, 75, 75))
        assertFalse(m.nested)
        assertTrue(m.segments.isEmpty())
    }

    @Test
    fun `nested elements give the inner element's insets on every side`() {
        val button = Bounds(0, 0, 200, 60)
        val label = Bounds(24, 16, 176, 44)
        val m = Measurer.measure(label, button)
        assertTrue(m.nested)
        assertEquals(
            mapOf(Side.Left to 24, Side.Top to 16, Side.Right to 24, Side.Bottom to 16),
            m.segments.associate { it.side to it.length },
        )
    }
}
