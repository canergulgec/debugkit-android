package dev.debugkit.layers.internal

import org.junit.Assert.assertEquals
import org.junit.Test

class DpFormatTest {

    // 450 dpi, as on a Pixel 5-sized emulator: dp values rarely land on whole pixels.
    private val density = 450 / 160f

    @Test
    fun `whole dp drawn on a fractional density reads as written`() {
        assertEquals("12", DpFormat.format(34f, density)) // 12dp = 33.75px, drawn as 34
        assertEquals("24", DpFormat.format(68f, density)) // 24dp = 67.5px, drawn as 68
        assertEquals("4", DpFormat.format(11f, density)) // 4dp = 11.25px, drawn as 11
        assertEquals("16", DpFormat.format(45f, density))
    }

    @Test
    fun `a real fraction keeps its decimal`() {
        // 12.5dp = 35.2px: more than a pixel away from both 12 and 13.
        assertEquals("12.5", DpFormat.format(35.2f, 2.8125f))
        assertEquals("76.4", DpFormat.format(76.4f * 3, 3f))
    }

    @Test
    fun `a hairline is never shown as zero`() {
        assertEquals("0.3", DpFormat.format(1f, 3f))
        assertEquals("0", DpFormat.format(0f, 3f))
    }

    @Test
    fun `whole densities are exact`() {
        assertEquals("16", DpFormat.format(48f, 3f))
        assertEquals("16.3", DpFormat.format(49f, 3f))
    }
}
