package dev.debugkit.plugin

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.debugkit.internal.deeplinks.AppLinksStatus
import dev.debugkit.internal.deeplinks.DeepLinkOpener
import dev.debugkit.internal.deeplinks.DeepLinkStore
import dev.debugkit.internal.deeplinks.DomainState
import dev.debugkit.internal.deeplinks.LinkCheck
import dev.debugkit.internal.deeplinks.appLinksStatus

/** Opens deep links the way users reach them, and explains why one does not open the app. */
internal object DeepLinksPlugin : DebugPlugin {

    override val id: String = "debugkit.deeplinks"
    override val title: String = "Deep Links"
    override val summary: String = "Open links and check App Links verification"
    override val order: Int = 22

    @Composable
    override fun Content() {
        val context = LocalContext.current
        var link by rememberSaveable { mutableStateOf("") }
        val check = remember(link) { DeepLinkOpener.check(context, link) }
        var failure by remember { mutableStateOf<String?>(null) }

        fun open(inApp: Boolean) {
            val opened = if (inApp) DeepLinkOpener.openInApp(context, link) else DeepLinkOpener.openAsLinkTap(context, link)
            if (opened) {
                DeepLinkStore.remember(link.trim())
                failure = null
            } else {
                failure = if (inApp) "No screen in this app opens this link." else "No app on this device opens this link."
            }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = link,
                        onValueChange = {
                            link = it
                            failure = null
                        },
                        label = { Text("Link") },
                        placeholder = { Text("myapp://product/123") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (link.isNotBlank()) LinkReport(check)
                    failure?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { open(inApp = true) }, enabled = check != null) { Text("Open in app") }
                        OutlinedButton(onClick = { open(inApp = false) }, enabled = check != null) { Text("Open like a link tap") }
                    }
                    Text(
                        "\"Open like a link tap\" lets the system choose, as when the link is tapped in a browser or chat: " +
                            "this app, a browser, or a chooser.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (DeepLinkStore.saved.isNotEmpty()) {
                item { SectionHeader("Saved links") }
                items(DeepLinkStore.saved, key = { "saved/${it.uri}" }) { saved ->
                    LinkRow(title = saved.title, uri = saved.uri, onPick = { link = saved.uri })
                }
            }

            if (DeepLinkStore.recent.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { SectionHeader("Recent") }
                        TextButton(onClick = DeepLinkStore::clearRecent, modifier = Modifier.padding(end = 8.dp, top = 16.dp)) { Text("Clear") }
                    }
                }
                items(DeepLinkStore.recent.toList(), key = { "recent/$it" }) { uri ->
                    LinkRow(title = null, uri = uri, onPick = { link = uri })
                }
            }

            item { SectionHeader("App Links") }
            item { AppLinks() }
        }
    }

    /** The link's parts and which screen of the app would open it. */
    @Composable
    private fun LinkReport(check: LinkCheck?) {
        if (check == null) {
            Text("Not a link yet: it needs a scheme, such as myapp:// or https://.", style = MaterialTheme.typography.bodySmall)
            return
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            check.parts.forEach { (label, value) ->
                Row {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.3f))
                    Text(value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.weight(0.7f))
                }
            }
            when {
                check.handler != null -> Status(GOOD, "Opens ${check.handler.substringAfterLast('.')}")
                check.handlerWithoutBrowsable != null -> Status(
                    WARNING,
                    "Only ${check.handlerWithoutBrowsable.substringAfterLast('.')} matches, and its intent filter lacks " +
                        "<category android:name=\"android.intent.category.BROWSABLE\" />. The link works inside the app but " +
                        "not when tapped in a browser or chat app.",
                )
                else -> Status(BAD, "No screen in this app opens this link. Check the scheme, host and path in the manifest's intent filters.")
            }
        }
    }

    @Composable
    private fun LinkRow(title: String?, uri: String, onPick: () -> Unit) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onPick).padding(horizontal = 16.dp, vertical = 10.dp)) {
            title?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
            Text(
                text = uri,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    @Composable
    private fun AppLinks() {
        val context = LocalContext.current
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Hint("App Links verification status needs Android 12 or newer.")
            return
        }
        var status by remember { mutableStateOf<AppLinksStatus?>(null) }
        // Re-read when returning from the system settings, where the user may change it.
        LifecycleResumeEffect(Unit) {
            status = appLinksStatus(context)
            onPauseOrDispose { }
        }
        val current = status
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "An https link opens this app directly only if its domain is verified (or the user picked this app). " +
                    "Otherwise it opens in the browser or asks.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                current == null -> Hint("Could not read the verification status.")
                current.domains.isEmpty() -> Hint("The app declares no verifiable https domains (intent filters with android:autoVerify=\"true\").")
                else -> {
                    if (!current.linkHandlingAllowed) {
                        Status(BAD, "Opening supported links is turned off for this app in system settings.")
                    }
                    current.domains.forEach { (host, state) ->
                        when (state) {
                            DomainState.Verified -> Status(GOOD, "$host · verified, opens the app")
                            DomainState.SelectedByUser -> Status(GOOD, "$host · not verified, but the user chose this app")
                            DomainState.NotVerified -> Status(WARNING, "$host · not verified, opens the browser or asks")
                        }
                    }
                }
            }
            TextButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, "package:${context.packageName}".toUri())
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            ) { Text("Open \"Open by default\" settings") }
        }
    }

    @Composable
    private fun Status(color: Color, text: String) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
            Box(Modifier.padding(top = 5.dp).size(8.dp).background(color, CircleShape))
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
        }
    }

    @Composable
    private fun Hint(text: String) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
    }

    private val GOOD = Color(0xFF388E3C)
    private val WARNING = Color(0xFFEF6C00)
    private val BAD = Color(0xFFD32F2F)
}
