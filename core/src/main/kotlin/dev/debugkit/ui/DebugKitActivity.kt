package dev.debugkit.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.debugkit.internal.PluginRegistry

/**
 * Hosts the DebugKit UI. Runs in its own task (see the manifest's `taskAffinity`) so it shows
 * up separately in Recents and never disturbs the host app's back stack.
 */
internal class DebugKitActivity : ComponentActivity() {

    /** Plugin to open, from the launching intent; cleared once the UI has switched to it. */
    private var requestedPluginId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // On recreation the UI restores its own selection; only honour a fresh launch.
        if (savedInstanceState == null) requestedPluginId = intent.getStringExtra(EXTRA_PLUGIN_ID)
        setContent {
            DebugKitTheme {
                DebugKitApp(
                    plugins = PluginRegistry.plugins,
                    requestedPluginId = requestedPluginId,
                    onPluginRequestHandled = { requestedPluginId = null },
                    onClose = ::finish,
                )
            }
        }
    }

    // singleTask: opening DebugKit again (e.g. from a notification) arrives here.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        requestedPluginId = intent.getStringExtra(EXTRA_PLUGIN_ID)
    }

    override fun onResume() {
        super.onResume()
        isVisible = true
    }

    override fun onPause() {
        isVisible = false
        super.onPause()
    }

    companion object {
        const val EXTRA_PLUGIN_ID = "dev.debugkit.PLUGIN_ID"

        /** Lets shake-to-open skip reopening while DebugKit is already on screen. */
        @Volatile
        var isVisible: Boolean = false
            private set
    }
}
