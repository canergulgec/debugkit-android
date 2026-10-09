package dev.debugkit.logs

import android.util.Log

/** Release stand-in for `:logs`'s LogsConfig. Values are accepted and ignored. */
public class LogsConfig internal constructor() {

    public var minPriority: Int = Log.VERBOSE
}
