package dev.debugkit.layers.internal

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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
        val nodes = runCatching { HierarchyCapture.capture(activity) }.getOrDefault(emptyList())
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
            // View.draw() cannot render.
            PixelCopy.request(
                activity.window,
                bitmap,
                { result -> publish(if (result == PixelCopy.SUCCESS) bitmap else null) },
                mainHandler,
            )
        } catch (_: IllegalArgumentException) {
            // The window has no surface yet or any more.
            publish(null)
        }
    }
}
