package dev.debugkit.layers.internal

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.view.inspector.WindowInspector

/**
 * The windows an activity's screen is made of: its own, then the dialogs, bottom sheets and
 * popups shown over it, bottom to top. Compose `Dialog`, `ModalBottomSheet` and `Popup` each get
 * a window of their own, so reading the activity's window alone leaves them out.
 */
internal object AppWindows {

    fun roots(activity: Activity): List<View> {
        val decor = activity.window.decorView
        val others = allRoots().filter { it !== decor && it.isShown && it.context.activity() === activity }
        return listOf(decor) + others
    }

    /** What a window is, for the root node's properties. */
    fun describe(root: View): String = when ((root.layoutParams as? WindowManager.LayoutParams)?.type) {
        WindowManager.LayoutParams.TYPE_BASE_APPLICATION -> "Activity"
        WindowManager.LayoutParams.TYPE_APPLICATION -> "Dialog or bottom sheet"
        in WindowManager.LayoutParams.FIRST_SUB_WINDOW..WindowManager.LayoutParams.LAST_SUB_WINDOW -> "Popup"
        else -> "Window"
    }

    /** Every root view of this process, in the order they were added, which is bottom to top. */
    private fun allRoots(): List<View> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WindowInspector.getGlobalWindowViews()
        } else {
            legacyRoots()
        }

    /** Before Android 10 the list is only reachable through WindowManagerGlobal, a hidden class. */
    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    private fun legacyRoots(): List<View> = runCatching {
        val global = Class.forName("android.view.WindowManagerGlobal")
        val instance = global.getMethod("getInstance").invoke(null)
        val views = global.getDeclaredField("mViews").apply { isAccessible = true }.get(instance)
        @Suppress("UNCHECKED_CAST")
        (views as List<View>).toList()
    }.getOrDefault(emptyList())

    private fun Context.activity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.activity()
        else -> null
    }
}
