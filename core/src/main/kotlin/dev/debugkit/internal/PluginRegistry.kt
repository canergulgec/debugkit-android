package dev.debugkit.internal

import androidx.compose.runtime.mutableStateListOf
import dev.debugkit.plugin.DebugPlugin

/** Holds registered plugins in menu order. Snapshot state, so the open menu updates live. */
internal object PluginRegistry {

    private val registered = mutableStateListOf<DebugPlugin>()

    val plugins: List<DebugPlugin> get() = registered

    @Synchronized
    fun register(plugin: DebugPlugin) {
        if (registered.any { it.id == plugin.id }) return
        // Insert after every plugin with the same or lower order, keeping registration order stable.
        val index = registered.indexOfFirst { it.order > plugin.order }.takeIf { it >= 0 } ?: registered.size
        registered.add(index, plugin)
    }
}
