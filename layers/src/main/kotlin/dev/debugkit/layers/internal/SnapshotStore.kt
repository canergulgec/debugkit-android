package dev.debugkit.layers.internal

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Picture
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

/**
 * Holds the latest capture of the app screen. Capturing happens just before DebugKit opens,
 * because once DebugKit covers the app its window is no longer drawn and cannot be copied.
 */
internal object SnapshotStore {

    var latest by mutableStateOf<LayerSnapshot?>(null)
        private set

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Main thread. Calls [done] once the snapshot is stored, with or without a screenshot. */
    fun capture(activity: Activity, done: () -> Unit) {
        val roots = runCatching { AppWindows.roots(activity) }.getOrDefault(listOf(activity.window.decorView))
        val nodes = runCatching { HierarchyCapture.capture(activity, roots) }.getOrDefault(emptyList())
        val decor = activity.window.decorView
        val capturedAt = System.currentTimeMillis()

        fun publish(bitmap: Bitmap?) {
            latest = LayerSnapshot(
                screen = activity.javaClass.name,
                capturedAt = capturedAt,
                density = activity.resources.displayMetrics.density,
                width = decor.width,
                height = decor.height,
                bitmap = bitmap,
                nodes = nodes,
            )
            done()
        }

        if (decor.width <= 0 || decor.height <= 0) return publish(null)
        val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        try {
            // Copies what is on screen, including hardware bitmaps and SurfaceViews that
            // View.draw() cannot render. Only the activity's own window, though.
            PixelCopy.request(
                activity.window,
                bitmap,
                { result ->
                    if (result == PixelCopy.SUCCESS) {
                        drawWindowsOnTop(bitmap, roots)
                        publish(bitmap)
                    } else {
                        publish(null)
                    }
                },
                mainHandler,
            )
        } catch (_: IllegalArgumentException) {
            // The window has no surface yet or any more.
            publish(null)
        }
    }

    /** Adds the dialogs, sheets and popups over the activity's window, each with the dim behind it. */
    private fun drawWindowsOnTop(screen: Bitmap, roots: List<View>) {
        val canvas = Canvas(screen)
        val (originX, originY) = HierarchyCapture.windowOrigin(roots.first())
        roots.drop(1).forEach { root ->
            val params = root.layoutParams as? WindowManager.LayoutParams
            if (params != null && params.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0) {
                canvas.drawColor(Color.argb((params.dimAmount * 255).roundToInt(), 0, 0, 0))
            }
            val window = render(root) ?: return@forEach
            val (x, y) = HierarchyCapture.windowOrigin(root)
            canvas.drawBitmap(window, (x - originX).toFloat(), (y - originY).toFloat(), null)
        }
    }

    /**
     * Draws one window's views. Through a Picture on Android 9+, so hardware bitmaps (Coil's
     * default for images) render too; on older versions such a window is left out.
     */
    private fun render(root: View): Bitmap? = runCatching {
        if (root.width <= 0 || root.height <= 0) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val picture = Picture()
            root.draw(picture.beginRecording(root.width, root.height))
            picture.endRecording()
            val rendered = Bitmap.createBitmap(picture)
            // A hardware bitmap cannot be drawn on the screenshot's software canvas.
            if (rendered.config == Bitmap.Config.HARDWARE) rendered.copy(Bitmap.Config.ARGB_8888, false) else rendered
        } else {
            Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also { root.draw(Canvas(it)) }
        }
    }.getOrNull()
}
