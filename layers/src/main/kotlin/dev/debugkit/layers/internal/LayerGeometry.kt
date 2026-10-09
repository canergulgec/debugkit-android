package dev.debugkit.layers.internal

/** Pure helpers over a snapshot's nodes, kept apart from Android views so they can be unit tested. */
internal object LayerGeometry {

    /**
     * The element a tap at window pixel ([x], [y]) means: the deepest node containing the point,
     * among nodes whose depth is in [depths]. Among equally deep matches, the last one wins,
     * since later siblings draw on top.
     */
    fun hitTest(nodes: List<LayerNode>, x: Int, y: Int, depths: IntRange): LayerNode? =
        nodes.withIndex()
            .filter { (_, node) -> node.depth in depths && node.bounds.contains(x, y) }
            .maxWithOrNull(compareBy<IndexedValue<LayerNode>> { it.value.depth }.thenBy { it.index })
            ?.value

    /**
     * What a finger tap at ([x], [y]) most likely means. A tap right on an element picks it, as
     * [hitTest] does. But fingers are wide and the screenshot is drawn small, so a tap that only
     * hits background, or a container covering much of the screen, snaps to the nearest smaller
     * element within [tolerance] pixels instead of selecting the whole screen.
     */
    fun pick(nodes: List<LayerNode>, x: Int, y: Int, tolerance: Int, screenWidth: Int, screenHeight: Int): LayerNode? {
        val direct = hitTest(nodes, x, y, 0..Int.MAX_VALUE)
        // A quarter of the screen: bigger than any control, smaller than page-level containers.
        val large = screenWidth.toLong() * screenHeight / 4
        if (direct != null && direct.bounds.area < large) return direct
        val nearby = nodes.withIndex()
            .filter { (_, node) -> node.bounds.area in 1 until large }
            .map { (index, node) -> Triple(node, node.bounds.distanceTo(x, y), index) }
            .filter { it.second <= tolerance }
            // Closest first; then the deeper element, then the one drawn on top.
            .minWithOrNull(compareBy<Triple<LayerNode, Double, Int>> { it.second }.thenByDescending { it.first.depth }.thenByDescending { it.third })
        return nearby?.first ?: direct
    }

    /**
     * The level each node is drawn at in 3D. A node that is its parent's only child and exactly
     * covers it stays on the parent's level: stacks of identical full-screen wrappers (DecorView,
     * content frame, ComposeView, …) would otherwise add slices that look the same, and steps of
     * the depth slider that change nothing. [nodes] must be in pre-order.
     */
    fun visualLevels(nodes: List<LayerNode>): Map<Int, Int> {
        val byId = nodes.associateBy { it.id }
        val childCount = nodes.groupingBy { it.parent }.eachCount()
        val levels = HashMap<Int, Int>(nodes.size)
        nodes.forEach { node ->
            val parent = byId[node.parent]
            levels[node.id] = when {
                parent == null -> 0
                childCount[parent.id] == 1 && parent.bounds == node.bounds -> levels.getValue(parent.id)
                else -> levels.getValue(parent.id) + 1
            }
        }
        return levels
    }

    /**
     * Re-orders nodes so each parent is followed by its subtree, keeping sibling order. Needed
     * because Views hosted inside Compose are found after the Compose nodes they belong under.
     */
    fun preOrder(nodes: List<LayerNode>): List<LayerNode> {
        val children = nodes.groupBy { it.parent }
        val result = ArrayList<LayerNode>(nodes.size)
        fun visit(node: LayerNode) {
            result += node
            children[node.id].orEmpty().forEach(::visit)
        }
        val ids = nodes.mapTo(HashSet()) { it.id }
        nodes.filter { it.parent !in ids }.forEach(::visit)
        return result
    }
}
