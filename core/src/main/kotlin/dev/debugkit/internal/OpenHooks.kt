package dev.debugkit.internal

import android.app.Activity
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Work feature modules need done on the app screen just before DebugKit covers it, such as the
 * layers module capturing a screenshot. DebugKit opens once every hook has called back, or after
 * [TIMEOUT_MS] at the latest, so a slow or broken hook can never keep it closed.
 */
internal object OpenHooks {

    private const val TIMEOUT_MS = 500L

    private val hooks = mutableListOf<(Activity, () -> Unit) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Synchronized
    fun add(hook: (activity: Activity, done: () -> Unit) -> Unit) {
        hooks += hook
    }

    /** Main thread only. Runs [open] exactly once. */
    fun runThenOpen(activity: Activity, open: () -> Unit) {
        val current = synchronized(this) { hooks.toList() }
        if (current.isEmpty()) return open()

        val opened = AtomicBoolean(false)
        // One Runnable instance, so removeCallbacks below cancels the same object it posted.
        val openOnce = Runnable { if (opened.compareAndSet(false, true)) open() }
        var pending = current.size
        mainHandler.postDelayed(openOnce, TIMEOUT_MS)
        current.forEach { hook ->
            val done = {
                mainHandler.post {
                    if (--pending == 0) {
                        mainHandler.removeCallbacks(openOnce)
                        openOnce.run()
                    }
                }
                Unit
            }
            runCatching { hook(activity, done) }.onFailure { done() }
        }
    }
}
