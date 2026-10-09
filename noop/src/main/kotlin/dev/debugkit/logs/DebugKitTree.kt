package dev.debugkit.logs

import timber.log.Timber

/** Release stand-in for `:logs`'s DebugKitTree. Declines every log, so nothing is recorded. */
public class DebugKitTree @JvmOverloads constructor(
    @Suppress("UNUSED_PARAMETER") configure: LogsConfig.() -> Unit = {},
) : Timber.Tree() {

    // Returning false makes Timber skip message formatting for this tree.
    override fun isLoggable(tag: String?, priority: Int): Boolean = false

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?): Unit = Unit
}
