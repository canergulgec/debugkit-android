package dev.debugkit

import android.app.Application
import android.content.Context

/**
 * Release stand-in for `:core`'s DebugKit. Same signatures, does nothing, and always returns
 * the app's own values, so release builds behave exactly as if DebugKit were not there.
 */
public object DebugKit {

    public val isEnabled: Boolean get() = false

    @Suppress("UNUSED_PARAMETER")
    public fun install(application: Application, configure: DebugKitConfig.() -> Unit = {}): Unit = Unit

    @Suppress("UNUSED_PARAMETER")
    public fun open(context: Context): Unit = Unit

    public fun environmentUrl(fallback: String): String = fallback

    @Suppress("UNUSED_PARAMETER")
    public fun flag(key: String, default: Boolean): Boolean = default

    @Suppress("UNUSED_PARAMETER")
    public fun flag(key: String, default: String): String = default
}
