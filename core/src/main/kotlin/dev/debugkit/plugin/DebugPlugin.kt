package dev.debugkit.plugin

import androidx.compose.runtime.Composable
import dev.debugkit.ui.DebugTone

/** One page of DebugKit, such as the network inspector or the log viewer. */
public interface DebugPlugin {

    /** Stable, unique key. Registering a second plugin with the same id is ignored. */
    public val id: String

    /** Name shown in its tab's page switcher, or in the App list. */
    public val title: String

    /** Optional one-line description, shown under [title] where plugins are listed. */
    public val summary: String? get() = null

    /**
     * The tab the page lives in. Plugins of one tab are switched with the bar at its top;
     * App-tab plugins are listed and open on their own. Custom plugins default to App.
     */
    public val section: DebugSection get() = DebugSection.App

    /** Position within its tab, lowest first; equal orders keep registration order. */
    public val order: Int get() = 50

    /** The page. Rendered below the tab's title and page switcher. */
    @Composable
    public fun Content()

    /**
     * An at-a-glance number for the Home tab, e.g. "128 calls, 3 failed", or null for none.
     * Composable so it can observe the plugin's live state.
     */
    @Composable
    public fun tile(): DebugTile? = null
}

/** The tabs of DebugKit's bottom bar, grouped by what QA is doing. Home is always there. */
public enum class DebugSection(public val label: String) {
    /** What did the app send and get back? */
    Network("Network"),

    /** What happened in the app? */
    Activity("Activity"),

    /** How do I trigger or inspect this? */
    Test("Test"),

    /** How is the app configured? */
    App("App"),
}

/** A Home tile: a big [value], a [label] above it and an optional [note] in [tone] below. */
public class DebugTile(
    public val label: String,
    public val value: String,
    public val note: String? = null,
    public val tone: DebugTone = DebugTone.Neutral,
)
