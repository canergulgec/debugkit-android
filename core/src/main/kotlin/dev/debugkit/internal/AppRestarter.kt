package dev.debugkit.internal

import android.content.Context
import android.content.Intent

/**
 * Relaunches the host app in a fresh process, so settings read once at startup (base URL,
 * DI graph, flags cached in singletons) take effect.
 */
internal object AppRestarter {

    fun restart(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        val component = launch.component ?: return
        // The system starts the new task even though this process exits right after.
        context.startActivity(Intent.makeRestartActivityTask(component))
        Runtime.getRuntime().exit(0)
    }
}
