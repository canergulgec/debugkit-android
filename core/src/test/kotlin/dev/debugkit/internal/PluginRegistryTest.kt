package dev.debugkit.internal

import androidx.compose.runtime.Composable
import dev.debugkit.plugin.DebugPlugin
import org.junit.Assert.assertEquals
import org.junit.Test

class PluginRegistryTest {

    private class Plugin(override val id: String, override val order: Int) : DebugPlugin {
        override val title: String = id

        @Composable
        override fun Content() = Unit
    }

    @Test
    fun `plugins are kept sorted by order, then registration, without duplicates`() {
        // The registry is a process-wide singleton; use ids no other test registers.
        listOf(
            Plugin("t.info", 90),
            Plugin("t.network", 10),
            Plugin("t.custom-a", 50),
            Plugin("t.actions", 0),
            Plugin("t.custom-b", 50),
            Plugin("t.network", 99),
        ).forEach(PluginRegistry::register)

        val ids = PluginRegistry.plugins.map { it.id }.filter { it.startsWith("t.") }
        assertEquals(listOf("t.actions", "t.network", "t.custom-a", "t.custom-b", "t.info"), ids)
    }
}
