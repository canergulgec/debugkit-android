package dev.debugkit.logs

import android.util.Log

/** Options for [DebugKitTree]. Mirrored in `:noop`. */
public class LogsConfig internal constructor() {

    /** Lowest priority recorded, as an `android.util.Log` constant. Everything is recorded by default. */
    public var minPriority: Int = Log.VERBOSE
}
