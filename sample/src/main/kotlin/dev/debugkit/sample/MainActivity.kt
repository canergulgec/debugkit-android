package dev.debugkit.sample

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import dev.debugkit.DebugKit

class MainActivity : ComponentActivity() {

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
