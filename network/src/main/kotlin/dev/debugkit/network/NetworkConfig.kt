package dev.debugkit.network

/** Options for [DebugKitInterceptor]. Mirrored in `:noop`. */
public class NetworkConfig internal constructor() {

    /** Header values replaced with `••••` before they are stored or shown. Case-insensitive. */
    public var redactHeaders: Set<String> = setOf(
        "Authorization",
        "Proxy-Authorization",
        "Cookie",
        "Set-Cookie",
    )

    /** Bodies larger than this many bytes are cut off in the inspector. The app still gets all of it. */
    public var maxContentLength: Long = 250_000L

    /**
     * Shows a silent notification with the number of calls this session and the latest ones.
     * Needs the notification permission on Android 13+, which DebugKit offers to request from
     * the Network screen.
     */
    public var showNotification: Boolean = true
}
