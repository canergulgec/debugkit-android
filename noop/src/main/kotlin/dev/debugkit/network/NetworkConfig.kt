package dev.debugkit.network

/** Release stand-in for `:network`'s NetworkConfig. Values are accepted and ignored. */
public class NetworkConfig internal constructor() {

    public var redactHeaders: Set<String> = emptySet()

    public var maxContentLength: Long = 0L

    public var showNotification: Boolean = false
}
