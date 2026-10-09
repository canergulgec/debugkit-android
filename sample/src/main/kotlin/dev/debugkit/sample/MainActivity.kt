package dev.debugkit.sample

import android.os.Bundle
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
                        // Flag reads inside composition recompose when QA changes an override.
                        Text(
                            text = "Base URL: ${app.baseUrl}\n" +
                                "new_checkout: ${DebugKit.flag("new_checkout", default = false)}\n" +
                                "onboarding_variant: ${DebugKit.flag("onboarding_variant", default = "A")}\n" +
                                "promo_code: ${DebugKit.flag("promo_code", default = "").ifEmpty { "–" }}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = { SampleRequests.sendAll(app.httpClient) }) {
                            Text("Send sample requests")
                        }
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
