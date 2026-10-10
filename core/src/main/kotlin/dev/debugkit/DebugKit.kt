package dev.debugkit

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import dev.debugkit.internal.CrashAttachments
import dev.debugkit.internal.DebugSettings
import dev.debugkit.internal.OpenHooks
import dev.debugkit.internal.PluginRegistry
import dev.debugkit.internal.ScreenTracker
import dev.debugkit.internal.ShakeToOpen
import dev.debugkit.internal.analytics.AnalyticsStore
import dev.debugkit.internal.crash.CrashReporter
import dev.debugkit.plugin.AppInfoPlugin
import dev.debugkit.plugin.CrashesPlugin
import dev.debugkit.plugin.DebugPlugin
import dev.debugkit.plugin.EnvironmentPlugin
import dev.debugkit.plugin.FeatureFlagsPlugin
import dev.debugkit.plugin.QuickActionsPlugin
import dev.debugkit.ui.DebugKitActivity

/**
 * Entry point of DebugKit.
 *
 * Host apps call [install] from `Application.onCreate` and depend on `:core` in debug builds
 * and on `:noop` in release builds. Everything a host app calls from its main source set
 * must exist with the same signature in `:noop`.
 */
public object DebugKit {

    /** `true` in this module, `false` in `:noop`. Lets host apps hide debug-only UI. */
    public val isEnabled: Boolean get() = true

    @Volatile
    private var installed = false

    /** Installs DebugKit. Safe to call more than once; only the first call takes effect. */
    @Synchronized
    public fun install(application: Application, configure: DebugKitConfig.() -> Unit = {}) {
        if (installed) return

        val config = DebugKitConfig().apply(configure)
        DebugSettings.init(application, config)
        installed = true

        ScreenTracker.install(application)
        if (config.captureCrashes) {
            CrashReporter.install(application, showDialogOnNextLaunch = config.showCrashDialog)
            PluginRegistry.register(CrashesPlugin)
        }
        if (config.actions.isNotEmpty()) PluginRegistry.register(QuickActionsPlugin)
        if (config.environments.isNotEmpty()) PluginRegistry.register(EnvironmentPlugin)
        // Shown even with no declared flags: flags the app reads are discovered at runtime.
        PluginRegistry.register(FeatureFlagsPlugin)
        PluginRegistry.register(AppInfoPlugin())
        if (config.shakeToOpen) ShakeToOpen.install(application)
    }

    /**
     * The base URL of the environment chosen in DebugKit, or [fallback] when none is chosen.
     * `:noop` always returns [fallback], so pass the build's own URL and release builds can
     * never be pointed elsewhere. Read it after [install], typically when building the HTTP client.
     */
    public fun environmentUrl(fallback: String): String =
        if (installed) DebugSettings.environmentUrl(fallback) else fallback

    /**
     * The value of a boolean feature flag: QA's override if one is set in DebugKit, otherwise
     * [default] (pass the app's real value, e.g. from remote config). `:noop` always returns [default].
     * Reading it inside a composable recomposes when the override changes.
     */
    public fun flag(key: String, default: Boolean): Boolean =
        if (installed) DebugSettings.booleanFlag(key, default) else default

    /** The value of a choice or text feature flag. See the Boolean overload. */
    public fun flag(key: String, default: String): String =
        if (installed) DebugSettings.stringFlag(key, default) else default

    /**
     * Shows an analytics event the app has just sent in DebugKit's Analytics screen. Call it next
     * to the real call, e.g. in the app's analytics wrapper. DebugKit sends nothing anywhere.
     * For [provider] "Firebase" (the default), events that break Firebase's naming and size
     * limits, which Firebase silently drops, are flagged. `:noop` does nothing.
     */
    public fun recordAnalyticsEvent(name: String, params: Map<String, Any?> = emptyMap(), provider: String = "Firebase") {
        if (installed) AnalyticsStore.recordEvent(provider, name, AnalyticsStore.normalize(params))
    }

    /** The Bundle form, as passed to `FirebaseAnalytics.logEvent`. See the Map overload. */
    public fun recordAnalyticsEvent(name: String, params: Bundle?, provider: String = "Firebase") {
        if (installed) AnalyticsStore.recordEvent(provider, name, AnalyticsStore.bundleToMap(params))
    }

    /** Shows a user property the app has just set; null means it was cleared. `:noop` does nothing. */
    public fun recordUserProperty(name: String, value: String?, provider: String = "Firebase") {
        if (installed) AnalyticsStore.recordUserProperty(provider, name, value)
    }

    /** Opens the DebugKit screen, e.g. from a button in the host app's own debug menu. */
    public fun open(context: Context) {
        val start = { context.startActivity(intent(context)) }
        // Hooks need the app screen still in front, so they only run when one is.
        val activity = ScreenTracker.resumedActivity
        if (activity != null && Looper.myLooper() == Looper.getMainLooper()) {
            OpenHooks.runThenOpen(activity, start)
        } else {
            start()
        }
    }

    /**
     * Runs [hook] on the app screen in front just before DebugKit opens over it, for feature
     * modules that capture that screen; not part of `:noop`. Call `done` when finished; DebugKit
     * opens once every hook is done, or after half a second regardless.
     */
    public fun beforeOpen(hook: (activity: Activity, done: () -> Unit) -> Unit) {
        OpenHooks.add(hook)
    }

    /**
     * Intent that opens DebugKit, optionally straight on the plugin with [pluginId]. For feature
     * modules (notifications, shortcuts); not part of `:noop`.
     */
    public fun intent(context: Context, pluginId: String? = null): Intent =
        Intent(context, DebugKitActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(DebugKitActivity.EXTRA_PLUGIN_ID, pluginId)

    /**
     * Adds a screen to DebugKit. Feature modules register themselves; host apps can add their
     * own screens from debug-only code. Not part of `:noop`, because [DebugPlugin] is Compose UI.
     */
    public fun registerPlugin(plugin: DebugPlugin) {
        PluginRegistry.register(plugin)
    }

    /**
     * Adds a text file to crash records, produced by [content] when the app crashes; empty
     * content is left out. For feature modules (the logs module adds `logs.txt`); not part of
     * `:noop`. [content] runs on the crashing thread, often the main thread, so it must be quick
     * and must not touch a database.
     */
    public fun addCrashAttachment(fileName: String, content: () -> String) {
        CrashAttachments.add(fileName, content)
    }
}
