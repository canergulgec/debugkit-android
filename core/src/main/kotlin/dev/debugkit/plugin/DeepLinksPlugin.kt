package dev.debugkit.plugin

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.debugkit.internal.deeplinks.DeepLinkOpener
import dev.debugkit.internal.deeplinks.DeepLinkStore
import dev.debugkit.internal.deeplinks.DomainState
import dev.debugkit.internal.deeplinks.LinkCheck
import dev.debugkit.internal.deeplinks.appLinksStatus
import dev.debugkit.ui.DebugKitButton
import dev.debugkit.ui.DebugKitCard
import dev.debugkit.ui.DebugKitColors
import dev.debugkit.ui.DebugKitFonts
import dev.debugkit.ui.DebugKitSearchField

/** Opens deep links the way users reach them, and explains why one does not open the app. */
internal object DeepLinksPlugin : DebugPlugin {

    override val id: String = "debugkit.deeplinks"
    override val title: String = "Deep Links"
    override val summary: String = "Open links and check App Links verification"
    override val order: Int = 22
    override val section: DebugSection = DebugSection.Test

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

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DebugKitSearchField(
                        value = link,
                        onValueChange = {
                            link = it
                            failure = null
                        },
                        placeholder = "myapp://product/123",
                        monospace = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DebugKitButton("Open in app", onClick = { open(inApp = true) }, primary = true, enabled = check != null, modifier = Modifier.weight(1f))
                        DebugKitButton("Open like a link tap", onClick = { open(inApp = false) }, enabled = check != null, modifier = Modifier.weight(1f))
                    }
                    failure?.let { Text(it, color = DebugKitColors.Error, style = MaterialTheme.typography.bodySmall) }
                    if (link.isNotBlank()) LinkReport(check)
                    Text(
                        "\"Open like a link tap\" lets the system choose, as when the link is tapped in a browser or chat: " +
                            "this app, a browser, or a chooser.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DebugKitColors.TextFaint,
                    )
                }
            }

            if (DeepLinkStore.saved.isNotEmpty()) {
                item {
                    SectionHeader("Saved links")
                    CardGroup(DeepLinkStore.saved) { saved -> LinkRow(title = saved.title, uri = saved.uri, onPick = { link = saved.uri }) }
                }
            }

            if (DeepLinkStore.recent.isNotEmpty()) {
                item {
                    SectionHeader("Recent", actionLabel = "Clear", onAction = DeepLinkStore::clearRecent)
                    CardGroup(DeepLinkStore.recent.toList()) { uri -> LinkRow(title = null, uri = uri, onPick = { link = uri }) }
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
            Text("Not a link yet: it needs a scheme, such as myapp:// or https://.", style = MaterialTheme.typography.bodySmall, color = DebugKitColors.Warning)
            return
        }
        DebugKitCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                HorizontalDivider(color = DebugKitColors.Divider, modifier = Modifier.padding(vertical = 6.dp))
                check.parts.forEach { (label, value) ->
                    Row {
                        Text(label, style = MaterialTheme.typography.bodySmall, color = DebugKitColors.TextSecondary, modifier = Modifier.weight(0.3f))
                        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = DebugKitFonts.Mono), modifier = Modifier.weight(0.7f))
                    }
                }
            }
        }
    }

    @Composable
    private fun LinkRow(title: String?, uri: String, onPick: () -> Unit) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onPick).padding(horizontal = 14.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            title?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
            Text(
                text = uri,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = DebugKitFonts.Mono),
                color = if (title != null) DebugKitColors.TextSecondary else DebugKitColors.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    @Composable
    private fun AppLinks() {
        val context = LocalContext.current
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Hint("App Links verification status needs Android 12 or newer.", Modifier.padding(horizontal = 20.dp))
            return
        }
        // Read right away, not only on resume: this row is recreated whenever it scrolls back into
        // view, and starting from null would flash "Could not read" each time.
        var status by remember { mutableStateOf(appLinksStatus(context)) }
        // Re-read when returning from the system settings, where the user may change it.
        LifecycleResumeEffect(Unit) {
            status = appLinksStatus(context)
            onPauseOrDispose { }
        }
        val current = status
        DebugKitCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                current?.domains?.takeIf { it.isNotEmpty() }?.let { domains ->
                    val unverified = domains.count { it.second == DomainState.NotVerified }
                    Text(
                        text = if (unverified == 0) "All ${domains.size} domains open the app" else "$unverified of ${domains.size} domains not verified",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (unverified == 0) DebugKitColors.Good else DebugKitColors.Warning,
                    )
                }
                Text(
                    "An https link opens this app directly only if its domain is verified (or the user picked this app). " +
                        "Otherwise it opens in the browser or asks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DebugKitColors.TextSecondary,
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
                DebugKitButton(
                    "Open \"Open by default\" settings",
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, "package:${context.packageName}".toUri())
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }

    @Composable
    private fun Status(color: Color, text: String) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
            Box(Modifier.padding(top = 5.dp).size(8.dp).background(color, CircleShape))
            Text(text, style = MaterialTheme.typography.bodySmall, color = DebugKitColors.Text, modifier = Modifier.padding(start = 8.dp))
        }
    }

    @Composable
    private fun Hint(text: String, modifier: Modifier = Modifier) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = DebugKitColors.TextSecondary, modifier = modifier)
    }

    private val GOOD = DebugKitColors.Good
    private val WARNING = DebugKitColors.Warning
    private val BAD = DebugKitColors.Error
}
