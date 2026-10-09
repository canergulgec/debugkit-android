package dev.debugkit.plugin

import androidx.compose.runtime.Composable

/** One screen in DebugKit's menu, such as the network inspector or the log viewer. */
public interface DebugPlugin {

    /** Stable, unique key. Registering a second plugin with the same id is ignored. */
    public val id: String

    /** Name shown in the menu and in the top bar while the plugin is open. */
    public val title: String

    /** Optional one-line description shown under [title] in the menu. */
    public val summary: String? get() = null

    /**
     * Position in the menu, lowest first; plugins with equal order keep registration order.
     * Built-in plugins use 0 (Quick Actions), 10-19 (network), 20-29 (app settings) and 90 (App Info).
     */
    public val order: Int get() = 50

    /** The plugin's screen. Rendered below DebugKit's top bar. */
    @Composable
    public fun Content()
}
