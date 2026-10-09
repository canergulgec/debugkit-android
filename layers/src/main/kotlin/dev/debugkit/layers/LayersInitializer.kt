package dev.debugkit.layers

import android.content.Context
import androidx.startup.Initializer
import dev.debugkit.DebugKit
import dev.debugkit.layers.internal.SnapshotStore
import dev.debugkit.layers.ui.LayersPlugin

/**
 * Run by androidx.startup when the app starts. Adds the Layers screen and captures the app
 * screen each time DebugKit opens over it.
 */
internal class LayersInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        DebugKit.registerPlugin(LayersPlugin)
        DebugKit.beforeOpen(SnapshotStore::capture)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
