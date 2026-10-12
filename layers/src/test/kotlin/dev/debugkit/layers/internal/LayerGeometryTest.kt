package dev.debugkit.layers.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LayerGeometryTest {

    private fun node(id: Int, parent: Int, depth: Int, rect: Bounds) =
        LayerNode(id, parent, depth, LayerKind.View, "n$id", null, rect, emptyList())

    // root (0) > card (1) > [title (2), button (3)]; a dialog-like overlay (4) on the root.
    private val root = node(0, -1, 0, Bounds(0, 0, 100, 200))
    private val card = node(1, 0, 1, Bounds(10, 10, 90, 100))
    private val title = node(2, 1, 2, Bounds(20, 20, 80, 40))
    private val button = node(3, 1, 2, Bounds(20, 60, 80, 90))
    private val overlay = node(4, 0, 1, Bounds(0, 150, 100, 200))
    private val nodes = listOf(root, card, title, button, overlay)

    @Test
    fun `tap selects the deepest element under the finger`() {
        assertEquals(title, LayerGeometry.hitTest(nodes, 30, 30, 0..10))
        assertEquals(card, LayerGeometry.hitTest(nodes, 30, 50, 0..10))
        assertEquals(overlay, LayerGeometry.hitTest(nodes, 50, 160, 0..10))
        assertNull(LayerGeometry.hitTest(nodes, 500, 500, 0..10))
    }

    @Test
    fun `tap ignores elements outside the depth range`() {
        assertEquals(card, LayerGeometry.hitTest(nodes, 30, 30, 0..1))
        assertEquals(root, LayerGeometry.hitTest(nodes, 30, 30, 0..0))
    }

    @Test
    fun `among equally deep matches the later sibling wins`() {
        val back = node(5, 0, 1, Bounds(0, 0, 50, 50))
        val front = node(6, 0, 1, Bounds(0, 0, 50, 50))
        assertEquals(front, LayerGeometry.hitTest(listOf(root, back, front), 10, 10, 0..10))
    }

    @Test
    fun `pre-order puts late children right after their parent`() {
        // An AndroidView found after the Compose walk, attached under the card.
        val interop = node(7, 1, 2, Bounds(20, 92, 80, 98))
        val shuffled = listOf(root, card, title, button, overlay, interop)
        assertEquals(listOf(0, 1, 2, 3, 7, 4), LayerGeometry.preOrder(shuffled).map { it.id })
    }

    @Test
    fun `identical single-child wrappers share a 3D level`() {
        // decor > content (same size, only child) > compose (same size, only child) > [header, list]
        val decor = node(0, -1, 0, Bounds(0, 0, 100, 200))
        val content = node(1, 0, 1, Bounds(0, 0, 100, 200))
        val compose = node(2, 1, 2, Bounds(0, 0, 100, 200))
        val header = node(3, 2, 3, Bounds(0, 0, 100, 40))
        val list = node(4, 2, 3, Bounds(0, 40, 100, 200))
        // A full-size child with a sibling still gets its own level.
        val overlay = node(5, 2, 3, Bounds(0, 0, 100, 200))
        val levels = LayerGeometry.visualLevels(listOf(decor, content, compose, header, list, overlay))
        assertEquals(mapOf(0 to 0, 1 to 0, 2 to 0, 3 to 1, 4 to 1, 5 to 1), levels)
    }

    @Test
    fun `pick keeps a direct hit on a small element`() {
        // Screen 100x200, so "large" is 5000 px² or more: root, card and overlay are large; title and button are not.
        assertEquals(title, LayerGeometry.pick(nodes, 30, 30, tolerance = 8, screenWidth = 100, screenHeight = 200))
    }

    @Test
    fun `pick snaps a background tap to the nearest small element`() {
        // (50, 45) is inside the card (a large container here) but 5 px below the title.
        assertEquals(title, LayerGeometry.pick(nodes, 50, 45, tolerance = 8, screenWidth = 100, screenHeight = 200))
        // Between title (last row 39) and button (first row 60): 10 px from title, 11 from button.
        assertEquals(title, LayerGeometry.pick(nodes, 50, 49, tolerance = 12, screenWidth = 100, screenHeight = 200))
    }

    @Test
    fun `pick falls back to the direct hit when nothing small is near`() {
        assertEquals(overlay, LayerGeometry.pick(nodes, 50, 190, tolerance = 8, screenWidth = 100, screenHeight = 200))
        assertEquals(root, LayerGeometry.pick(nodes, 5, 120, tolerance = 4, screenWidth = 100, screenHeight = 200))
    }

    // A bottom sheet in its own window: a full-screen scrim root (8) holding the sheet (9) and its button (10).
    private val scrim = node(8, -1, 0, Bounds(0, 0, 100, 200)).copy(window = 1)
    private val sheet = node(9, 8, 1, Bounds(0, 120, 100, 200)).copy(window = 1)
    private val sheetButton = node(10, 9, 2, Bounds(10, 150, 90, 170)).copy(window = 1)
    private val withSheet = nodes + listOf(scrim, sheet, sheetButton)

    @Test
    fun `a window on top wins over deeper elements below it`() {
        // The app's title (depth 2) lies under the scrim (depth 0), which covers it.
        assertEquals(scrim, LayerGeometry.hitTest(withSheet, 30, 30, 0..10))
        assertEquals(sheetButton, LayerGeometry.hitTest(withSheet, 30, 160, 0..10))
    }

    @Test
    fun `pick never snaps to an element under a window on top`() {
        // A scrim tap 5 px below the app's title must not select the title.
        assertEquals(scrim, LayerGeometry.pick(withSheet, 50, 45, tolerance = 8, screenWidth = 100, screenHeight = 200))
    }

    @Test
    fun `a window starts above every level drawn before it`() {
        val levels = LayerGeometry.visualLevels(LayerGeometry.preOrder(withSheet))
        // The app reaches level 2 (title, button), so the sheet window starts at 3.
        assertEquals(3, levels[8])
        assertEquals(4, levels[9])
        assertEquals(5, levels[10])
    }
}
