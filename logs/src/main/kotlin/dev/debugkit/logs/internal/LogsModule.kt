package dev.debugkit.logs.internal

import dev.debugkit.DebugKit
import dev.debugkit.logs.ui.LogsPlugin

/** One-time setup shared by every DebugKitTree instance. */
internal object LogsModule {

    @Volatile
    private var initialized = false

    @Synchronized
    fun init() {
        if (initialized) return
        initialized = true
        DebugKit.registerPlugin(LogsPlugin)
        DebugKit.addCrashAttachment("logs.txt") {
            LogStore.snapshot().takeLast(MAX_ATTACHED_LOGS).joinToString("\n", transform = LogFormat::line)
        }
    }

    /** Enough to show what led up to a crash, small enough to share as text. */
    private const val MAX_ATTACHED_LOGS = 1_000
}
