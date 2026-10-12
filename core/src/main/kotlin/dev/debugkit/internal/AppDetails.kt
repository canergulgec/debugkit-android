package dev.debugkit.internal

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.pm.PackageInfoCompat
import java.lang.ref.WeakReference

internal fun Context.packageInfo(): PackageInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)
    }

internal val Context.isDebuggable: Boolean
    get() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

/** `1.4.0 (140)`: version name and code. */
internal val PackageInfo.versionLabel: String
    get() = "$versionName (${PackageInfoCompat.getLongVersionCode(this)})"

/** The facts every crash and report starts with: which build, on which device, against which backend. */
internal fun appSummary(context: Context): List<Pair<String, String>> =
    buildList {
        add("App" to "${context.packageName} ${context.packageInfo().versionLabel}${if (context.isDebuggable) " debuggable" else ""}")
        add("Device" to "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        add("Locale" to context.resources.configuration.locales[0].toLanguageTag())
        DebugSettings.selectedEnvironment?.let { add("Environment" to it) }
        if (DebugSettings.overrides.isNotEmpty()) {
            add("Flag overrides" to DebugSettings.overrides.entries.joinToString { "${it.key}=${it.value}" })
        }
        ScreenTracker.current?.let { add("Screen" to it) }
    }

/** Remembers the app screen in front, for crash reports. DebugKit's own screen is ignored. */
internal object ScreenTracker : ActivityCallbacks() {

    @Volatile
    var current: String? = null
        private set

    private var resumed: WeakReference<Activity>? = null

    /** The app screen in front right now, or null while DebugKit or another app is in front. */
    val resumedActivity: Activity? get() = resumed?.get()

    override fun onActivityResumed(activity: Activity) {
        if (activity.isDebugKit()) return
        current = activity.javaClass.name
        resumed = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumed?.get() === activity) resumed = null
    }

    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }
}

internal fun Activity.isDebugKit(): Boolean = javaClass.name.startsWith("dev.debugkit.ui.")

/** Lifecycle callbacks with empty defaults, so implementations override only what they need. */
internal abstract class ActivityCallbacks : Application.ActivityLifecycleCallbacks {
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
