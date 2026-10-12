package dev.debugkit.layers.internal

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.Resources
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.AccessibilityAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Walks an activity's windows into a flat list of [LayerNode]s: Views as they are, and Compose
 * content through its semantics tree, the public API Compose tests use. Views placed inside
 * Compose with AndroidView are attached under the Compose node that contains them. Dialogs,
 * bottom sheets and popups are their own windows; each is walked as a further root.
 */
internal class HierarchyCapture private constructor(private val activity: Activity) {

    private val nodes = mutableListOf<LayerNode>()
    private val resources: Resources = activity.resources
    private val density = resources.displayMetrics.density
    private val fontScale = resources.configuration.fontScale
    private val location = IntArray(2)

    // The window being walked, and where it sits relative to the activity's window.
    private var window = 0
    private var offsetX = 0
    private var offsetY = 0

    @SuppressLint("DiscouragedApi") // The fragment library is not a dependency, so its id is looked up.
    private val fragmentTagId = resources.getIdentifier("fragment_container_view_tag", "id", activity.packageName)

    private fun walkView(view: View, depth: Int, parent: Int, insideCompose: Boolean) {
        if (!view.isShown || view.width == 0 || view.height == 0) return

        if (view is RootForTest) {
            // AndroidComposeView: its parent ComposeView is already recorded, so record the
            // Compose content directly under it instead of this implementation detail.
            val composeStart = nodes.size
            view.semanticsOwner.unmergedRootSemanticsNode.children.forEach { walkSemantics(it, depth, parent) }
            if (view is ViewGroup) attachInteropViews(view, composeStart, depth, parent)
            return
        }

        val id = addView(view, depth, parent, if (insideCompose) LayerKind.Interop else LayerKind.View)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) walkView(view.getChildAt(i), depth + 1, id, insideCompose)
        }
    }

    private fun walkSemantics(node: SemanticsNode, depth: Int, parent: Int) {
        val rect = node.boundsInWindow
        if (rect.width <= 0f || rect.height <= 0f) return
        val bounds = Bounds(
            rect.left.roundToInt() + offsetX,
            rect.top.roundToInt() + offsetY,
            rect.right.roundToInt() + offsetX,
            rect.bottom.roundToInt() + offsetY,
        )
        val config = node.config

        val text = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
            ?: config.getOrNull(SemanticsProperties.EditableText)?.text
        val testTag = config.getOrNull(SemanticsProperties.TestTag)
        val role = config.getOrNull(SemanticsProperties.Role)
        val name = testTag ?: role?.toString() ?: when {
            text != null -> "Text"
            config.getOrNull(SemanticsActions.OnClick) != null -> "Clickable"
            else -> "Layout"
        }

        val attributes = buildList {
            add("Kind" to "Compose")
            testTag?.let { add("Test tag" to it) }
            role?.let { add("Role" to it.toString()) }
            addBounds(bounds)
            for ((key, value) in config) {
                if (key == SemanticsProperties.TestTag || key == SemanticsProperties.Role) continue
                add(key.name to describe(value))
            }
            val modifiers = runCatching { node.layoutInfo.getModifierInfo().map { modifierName(it.modifier) } }
                .getOrDefault(emptyList()).distinct()
            if (modifiers.isNotEmpty()) add("Modifiers" to modifiers.joinToString())
        }

        val id = add(
            depth = depth,
            parent = parent,
            kind = LayerKind.Compose,
            name = name,
            label = text?.take(LABEL_LENGTH) ?: config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull(),
            bounds = bounds,
            attributes = attributes,
        )
        node.children.forEach { walkSemantics(it, depth + 1, id) }
    }

    /**
     * AndroidComposeView keeps AndroidView content in holder Views. Each is placed under the
     * deepest Compose node, from [composeStart] on, whose bounds contain the View's centre.
     */
    private fun attachInteropViews(composeRoot: ViewGroup, composeStart: Int, depth: Int, parent: Int) {
        val composeNodes = nodes.subList(composeStart, nodes.size).toList()
        for (i in 0 until composeRoot.childCount) {
            val container = composeRoot.getChildAt(i) as? ViewGroup ?: continue
            for (j in 0 until container.childCount) {
                // Compose keeps other internal Views here too, such as ripple hosts; only
                // AndroidView holders carry app content.
                val holder = container.getChildAt(j) as? ViewGroup ?: continue
                if (!holder.isAndroidViewHolder()) continue
                val view = holder.takeIf { it.childCount == 1 }?.getChildAt(0) ?: holder
                if (!view.isShown || view.width == 0 || view.height == 0) continue
                val bounds = boundsOf(view)
                val host = composeNodes.filter { it.bounds.contains(bounds.centerX, bounds.centerY) }.maxByOrNull { it.depth }
                walkView(view, (host?.depth ?: depth - 1) + 1, host?.id ?: parent, insideCompose = true)
            }
        }
    }

    private fun addView(view: View, depth: Int, parent: Int, kind: LayerKind): Int {
        val idName = resourceName(view.id)
        val text = (view as? TextView)?.text?.toString()?.takeIf { it.isNotEmpty() }
        val bounds = boundsOf(view)

        val attributes = buildList {
            add("Kind" to if (kind == LayerKind.Interop) "View inside Compose (AndroidView)" else "View")
            add("Class" to view.javaClass.name)
            if (parent == -1) add("Window" to AppWindows.describe(view))
            idName?.let { add("Id" to it) }
            fragmentName(view)?.let { add("Fragment" to it) }
            addBounds(bounds)
            add("Padding" to "${dp(view.paddingLeft)}, ${dp(view.paddingTop)}, ${dp(view.paddingRight)}, ${dp(view.paddingBottom)} dp")
            (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                add("Margin" to "${dp(it.leftMargin)}, ${dp(it.topMargin)}, ${dp(it.rightMargin)}, ${dp(it.bottomMargin)} dp")
            }
            if (view.alpha < 1f) add("Alpha" to view.alpha.toString())
            if (view.elevation > 0f) add("Elevation" to "${dp(view.elevation)} dp")
            view.background?.let { add("Background" to ((it as? ColorDrawable)?.color?.let(::hex) ?: it.javaClass.simpleName)) }
            view.contentDescription?.let { add("Content description" to it.toString()) }
            add("Clickable" to view.isClickable.toString())
            if (!view.isEnabled) add("Enabled" to "false")
            if (view is TextView) {
                text?.let { add("Text" to it) }
                view.hint?.let { add("Hint" to it.toString()) }
                add("Text size" to "${(view.textSize / (density * fontScale) * 10).roundToInt() / 10f} sp")
                add("Text color" to hex(view.currentTextColor))
                view.typeface?.let { typeface ->
                    val style = when {
                        typeface.isBold && typeface.isItalic -> "bold italic"
                        typeface.isBold -> "bold"
                        typeface.isItalic -> "italic"
                        else -> "normal"
                    }
                    add("Font" to "$style${if (typeface == Typeface.DEFAULT || typeface == Typeface.DEFAULT_BOLD) "" else " (custom)"}")
                }
                if (view.maxLines in 1 until Int.MAX_VALUE) add("Max lines" to view.maxLines.toString())
            }
            if (view is ImageView) {
                add("Scale type" to view.scaleType.name)
                view.drawable?.let { add("Drawable" to it.javaClass.simpleName) }
            }
        }

        return add(
            depth = depth,
            parent = parent,
            kind = kind,
            name = view.javaClass.simpleName.ifEmpty { view.javaClass.name.substringAfterLast('.') },
            label = idName ?: text?.take(LABEL_LENGTH),
            bounds = bounds,
            attributes = attributes,
        )
    }

    private fun add(
        depth: Int,
        parent: Int,
        kind: LayerKind,
        name: String,
        label: String?,
        bounds: Bounds,
        attributes: List<Pair<String, String>>,
    ): Int {
        val id = nodes.size
        nodes += LayerNode(id, parent, depth, kind, name, label, bounds, attributes, window)
        return id
    }

    private fun MutableList<Pair<String, String>>.addBounds(bounds: Bounds) {
        add("Position" to "${bounds.left}, ${bounds.top} px (${dp(bounds.left)}, ${dp(bounds.top)} dp)")
        add("Size" to "${bounds.width} × ${bounds.height} px (${dp(bounds.width)} × ${dp(bounds.height)} dp)")
    }

    private fun boundsOf(view: View): Bounds {
        view.getLocationInWindow(location)
        val left = location[0] + offsetX
        val top = location[1] + offsetY
        return Bounds(left, top, left + view.width, top + view.height)
    }

    private fun resourceName(id: Int): String? =
        if (id == View.NO_ID || id ushr 24 == 0) null else runCatching { resources.getResourceEntryName(id) }.getOrNull()

    private fun fragmentName(view: View): String? =
        if (fragmentTagId == 0) null else view.getTag(fragmentTagId)?.javaClass?.simpleName

    private fun dp(px: Int): String = dp(px.toFloat())

    private fun dp(px: Float): String = DpFormat.format(px, density)

    companion object {
        private const val LABEL_LENGTH = 40

        /**
         * Main thread only: Views and Compose state must be read where they are drawn. [roots]
         * are the activity's windows bottom to top, its own first; see [AppWindows].
         */
        fun capture(activity: Activity, roots: List<View>): List<LayerNode> {
            val capture = HierarchyCapture(activity)
            val origin = windowOrigin(roots.first())
            roots.forEachIndexed { index, root ->
                val (x, y) = windowOrigin(root)
                capture.window = index
                capture.offsetX = x - origin.first
                capture.offsetY = y - origin.second
                capture.walkView(root, 0, -1, insideCompose = false)
            }
            return LayerGeometry.preOrder(capture.nodes)
        }

        /** Where a window's root sits on the screen. */
        fun windowOrigin(root: View): Pair<Int, Int> {
            val location = IntArray(2)
            root.getLocationOnScreen(location)
            return location[0] to location[1]
        }

        private const val ANDROID_VIEW_HOLDER = "androidx.compose.ui.viewinterop.AndroidViewHolder"

        /**
         * Matched by class name: the holder is not public API. In an obfuscated debug build the
         * name no longer matches and AndroidView content is simply left out.
         */
        private fun View.isAndroidViewHolder(): Boolean =
            generateSequence<Class<*>>(javaClass) { it.superclass }.any { it.name == ANDROID_VIEW_HOLDER }

        private fun hex(color: Int): String = String.format(Locale.US, "#%08X", color)

        private fun describe(value: Any?): String = when (value) {
            null, Unit -> "true"
            is AccessibilityAction<*> -> value.label ?: "yes"
            is List<*> -> value.joinToString { it.toString() }
            else -> value.toString()
        }

        /** `PaddingElement` -> `Padding`, `ClickableElement` -> `Clickable`. */
        private fun modifierName(modifier: Any): String =
            modifier.javaClass.simpleName.removeSuffix("ModifierElement").removeSuffix("Element").removeSuffix("Modifier")
                .ifEmpty { modifier.javaClass.name.substringAfterLast('.') }
    }
}
