package dev.debugkit.sample

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import dev.debugkit.DebugKit

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Scaffold { innerPadding ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val app = application as SampleApplication
                        // Shows what a tapped test notification ("Open app with data") passed in.
                        val pushData = intent.extras?.keySet()?.filter { it in listOf("type", "orderId") }
                            ?.joinToString { "$it=${intent.getStringExtra(it)}" }
                        if (!pushData.isNullOrEmpty()) Text("Opened from a notification: $pushData", color = MaterialTheme.colorScheme.primary)
                        // Flag reads inside composition recompose when QA changes an override.
                        Text(
                            text = "Base URL: ${app.baseUrl}\n" +
                                "new_checkout: ${DebugKit.flag("new_checkout", default = false)}\n" +
                                "onboarding_variant: ${DebugKit.flag("onboarding_variant", default = "A")}\n" +
                                "promo_code: ${DebugKit.flag("promo_code", default = "").ifEmpty { "–" }}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        LaunchedEffect(Unit) {
                            DebugKit.recordAnalyticsEvent("screen_view", mapOf("screen_name" to "Home", "screen_class" to "MainActivity"))
                        }
                        Button(
                            onClick = {
                                DebugKit.recordAnalyticsEvent("send_requests_click")
                                SampleRequests.sendAll(app.httpClient)
                            },
                        ) {
                            Text("Send sample requests")
                        }
                        Button(onClick = { startActivity(Intent(this@MainActivity, XmlActivity::class.java)) }) {
                            Text("Open XML screen")
                        }
                        // Dialogs and sheets live in windows of their own; the Layers inspector shows them over the screen.
                        var showDialog by remember { mutableStateOf(false) }
                        var showSheet by remember { mutableStateOf(false) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { showDialog = true }) { Text("Show dialog") }
                            Button(onClick = { showSheet = true }) { Text("Show bottom sheet") }
                        }
                        if (showDialog) {
                            AlertDialog(
                                onDismissRequest = { showDialog = false },
                                title = { Text("Delete order?") },
                                text = { Text("Shake the device now to inspect this dialog under Layers.") },
                                confirmButton = { TextButton(onClick = { showDialog = false }) { Text("Delete") } },
                                dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } },
                                modifier = Modifier.testTag("DeleteDialog"),
                            )
                        }
                        if (showSheet) {
                            ModalBottomSheet(onDismissRequest = { showSheet = false }, modifier = Modifier.testTag("FilterSheet")) {
                                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Filters", style = MaterialTheme.typography.titleLarge)
                                    Text("Shake the device now to inspect this sheet under Layers.")
                                    Button(onClick = { showSheet = false }, modifier = Modifier.fillMaxWidth()) { Text("Apply") }
                                }
                            }
                        }
                        // A classic View inside Compose, shown in orange by the Layers inspector.
                        AndroidView(
                            factory = { context ->
                                TextView(context).apply {
                                    id = R.id.legacy_banner
                                    text = "Legacy TextView via AndroidView"
                                    setPadding(32, 24, 32, 24)
                                    setBackgroundColor(0xFFFFF3E0.toInt())
                                }
                            },
                            modifier = Modifier.testTag("LegacyBanner"),
                        )
                        if (DebugKit.isEnabled) {
                            val context = LocalContext.current
                            Text("Shake the device or tap the button to open DebugKit.")
                            Button(onClick = { DebugKit.open(context) }) {
                                Text("Open DebugKit")
                            }
                        } else {
                            Text("Release build: DebugKit is replaced by the no-op module.")
                        }
                    }
                }
            }
        }
    }
}
