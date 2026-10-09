package dev.debugkit.sample

import android.os.Bundle
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.debugkit.DebugKit

/** XML layout hosting a ComposeView: View nodes with Compose nodes inside them. */
class XmlActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_xml)

        findViewById<ComposeView>(R.id.compose_island).setContent {
            MaterialTheme {
                Column(Modifier.testTag("ComposeIsland"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Compose inside XML", style = MaterialTheme.typography.titleMedium)
                    Text("This card is a ComposeView placed in a FrameLayout.")
                    OutlinedButton(onClick = {}, modifier = Modifier.testTag("ComposeButton")) { Text("Compose button") }
                }
            }
        }
        findViewById<Button>(R.id.xml_open_debugkit).setOnClickListener { DebugKit.open(this) }
    }
}
