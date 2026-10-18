package dev.debugkit.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.debugkit.internal.PluginRegistry
import java.lang.ref.WeakReference

/**
 * Hosts the DebugKit UI. Runs in its own task (see the manifest's `taskAffinity`) so it shows
 * up separately in Recents and never disturbs the host app's back stack.
 */
internal class DebugKitActivity : ComponentActivity() {

    /** Plugin to open, from the launching intent; cleared once the UI has switched to it. */
    private var requestedPluginId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Light status and navigation bar icons on DebugKit's dark background, whatever the system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        instance = WeakReference(this)
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

    override fun onDestroy() {
        if (instance?.get() === this) instance = null
        super.onDestroy()
        afterDestroyed?.let {
            afterDestroyed = null
            it()
        }
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

        private var instance: WeakReference<DebugKitActivity>? = null
        private var afterDestroyed: (() -> Unit)? = null

        /** Runs [action] once, after the open DebugKit screen is destroyed and its window is gone. */
        fun afterDestroyed(action: () -> Unit) {
            afterDestroyed = action
        }

        /** Finishes the open DebugKit screen, if any. Returns whether there was one. */
        fun finishCurrent(): Boolean {
            val activity = instance?.get() ?: return false
            activity.runOnUiThread { activity.finish() }
            return true
        }
    }
}
