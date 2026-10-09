package dev.debugkit.internal.crash

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import dev.debugkit.DebugKit
import dev.debugkit.internal.ActivityCallbacks
import dev.debugkit.internal.isDebugKit
import dev.debugkit.plugin.CrashesPlugin

/**
 * After a crash, tells QA on the first app screen of the next launch, with a shortcut to the
 * crash details. Shown once per crash.
 */
internal object CrashDialog {

    fun showOnNextScreen(application: Application, crash: CrashStore.Crash) {
        application.registerActivityLifecycleCallbacks(object : ActivityCallbacks() {
            override fun onActivityResumed(activity: Activity) {
                if (activity.isDebugKit()) return
                application.unregisterActivityLifecycleCallbacks(this)
                show(activity, crash)
            }
        })
    }

    private fun show(activity: Activity, crash: CrashStore.Crash) {
        CrashReporter.markSeen(crash)
        val headline = crash.exceptionClass.substringAfterLast('.') + (crash.message?.let { ": $it" } ?: "")
        AlertDialog.Builder(activity)
            .setTitle("The app crashed last time")
            .setMessage("$headline\n\n${CrashFormatter.formatTime(crash.timestamp)}")
            .setPositiveButton("View") { _, _ -> activity.startActivity(DebugKit.intent(activity, CrashesPlugin.id)) }
            .setNegativeButton("Dismiss", null)
            .show()
    }
}
