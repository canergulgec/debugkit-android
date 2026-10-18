package dev.debugkit

import android.app.Application
import android.content.Context
import android.os.Bundle

/**
 * Release stand-in for `:core`'s DebugKit. Same signatures, does nothing, and always returns
 * the app's own values, so release builds behave exactly as if DebugKit were not there.
 */
public object DebugKit {

    public val isEnabled: Boolean get() = false

    @Suppress("UNUSED_PARAMETER")
    public fun install(application: Application, configure: DebugKitConfig.() -> Unit = {}): Unit = Unit

    @Suppress("UNUSED_PARAMETER")
    public fun recordAnalyticsEvent(name: String, params: Map<String, Any?> = emptyMap(), provider: String = "Firebase"): Unit = Unit

    @Suppress("UNUSED_PARAMETER")
    public fun recordAnalyticsEvent(name: String, params: Bundle?, provider: String = "Firebase"): Unit = Unit

    @Suppress("UNUSED_PARAMETER")
    public fun recordUserProperty(name: String, value: String?, provider: String = "Firebase"): Unit = Unit

    @Suppress("UNUSED_PARAMETER")
    public fun recordPush(title: String?, body: String?, data: Map<String, String> = emptyMap()): Unit = Unit

    @Suppress("UNUSED_PARAMETER")
    public fun open(context: Context): Unit = Unit

    public fun close(then: () -> Unit = {}): Unit = then()

    public fun environmentUrl(fallback: String): String = fallback

    @Suppress("UNUSED_PARAMETER")
    public fun flag(key: String, default: Boolean): Boolean = default

    @Suppress("UNUSED_PARAMETER")
    public fun flag(key: String, default: String): String = default
}
