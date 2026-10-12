package dev.debugkit.plugin

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.debugkit.internal.push.PushData
import dev.debugkit.internal.push.PushDelivery
import dev.debugkit.internal.push.PushStore
import dev.debugkit.internal.push.PushTemplate
import dev.debugkit.internal.push.ReceivedPush
import dev.debugkit.internal.push.TestPushSender
import dev.debugkit.ui.DebugKitButton
import dev.debugkit.ui.DebugKitCard
import dev.debugkit.ui.DebugKitChip
import dev.debugkit.ui.DebugKitColors
import dev.debugkit.ui.DebugKitFonts
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Push token, test notifications that route like real ones, and the pushes the app received. */
internal object PushPlugin : DebugPlugin {

    override val id: String = "debugkit.push"
    override val title: String = "Push"
    override val order: Int = 23
    override val section: DebugSection = DebugSection.Test

    override val summary: String
        get() = when (val count = PushStore.received.size) {
            0 -> "Send test notifications, see received ones"
            1 -> "1 push received"
            else -> "$count pushes received"
        }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val setup = PushStore.setup

        var title by rememberSaveable { mutableStateOf("") }
        var body by rememberSaveable { mutableStateOf("") }
        var data by rememberSaveable { mutableStateOf("") }
        var link by rememberSaveable { mutableStateOf("") }
        var delivery by rememberSaveable { mutableStateOf(PushDelivery.LaunchWithData) }
        var error by remember { mutableStateOf<String?>(null) }
        var expanded by remember { mutableStateOf<Long?>(null) }

        fun fill(template: PushTemplate) {
            title = template.title
            body = template.body
            data = PushData.format(template.data)
            link = template.link.orEmpty()
            delivery = if (template.link.isNullOrBlank()) PushDelivery.LaunchWithData else PushDelivery.OpenLink
            error = null
        }

        fun current() = PushTemplate(title.trim(), body.trim(), PushData.parse(data), link.trim().ifEmpty { null })

        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        // Items before the form's header: permission, token header, token, received header, rows.
        val sendHeaderIndex = 4 + PushStore.received.size.coerceAtLeast(1)

        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            item { NotificationPermission() }

            item { SectionHeader("Push token") }
            item { Token(setup.token) }

            // Right under the token: what QA checks most, so it must not hide below the form.
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionHeader(if (PushStore.received.isEmpty()) "Received" else "Received (${PushStore.received.size})") }
                    if (PushStore.received.isNotEmpty()) {
                        Text(
                            "Clear",
                            color = DebugKitColors.Accent,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(end = 20.dp, top = 12.dp).clickable(onClick = PushStore::clearReceived),
                        )
                    }
                }
            }
            if (PushStore.received.isEmpty()) {
                item {
                    Text(
                        "None yet. Pushes appear here when the app calls DebugKit.recordPush in its messaging service.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DebugKitColors.TextSecondary,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
            items(PushStore.received.toList(), key = { it.timestamp to it.data.hashCode() }) { push ->
                ReceivedRow(
                    push = push,
                    expanded = expanded == push.timestamp,
                    onToggle = { expanded = if (expanded == push.timestamp) null else push.timestamp },
                    onResend = {
                        fill(PushTemplate(push.title.orEmpty(), push.body.orEmpty(), push.data))
                        // The form is below the list; bring it into view with the push filled in.
                        scope.launch { listState.animateScrollToItem(sendHeaderIndex) }
                    },
                )
            }
            item { SectionHeader("Send a test notification") }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(body, { body = it }, label = { Text("Body") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = data,
                        onValueChange = { data = it },
                        label = { Text("Data, one key=value per line") },
                        placeholder = { Text("type=order\norderId=123") },
                        minLines = 2,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = DebugKitFonts.Mono),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = link,
                        onValueChange = { link = it },
                        label = { Text("Link (optional)") },
                        placeholder = { Text("myapp://order/123") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = DebugKitFonts.Mono),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text("When tapped", style = MaterialTheme.typography.labelLarge, color = DebugKitColors.TextSecondary)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DebugKitChip("Open app with data", delivery == PushDelivery.LaunchWithData, { delivery = PushDelivery.LaunchWithData })
                        DebugKitChip("Open link", delivery == PushDelivery.OpenLink, { delivery = PushDelivery.OpenLink }, enabled = link.isNotBlank())
                        if (setup.handler != null) {
                            DebugKitChip("App's handler", delivery == PushDelivery.AppHandler, { delivery = PushDelivery.AppHandler })
                        }
                    }
                    Text(
                        text = when (delivery) {
                            PushDelivery.LaunchWithData ->
                                "Tapping opens the app's start screen with the data as intent extras, as Firebase does when the app is in the background."
                            PushDelivery.OpenLink -> "Tapping opens the link in this app."
                            PushDelivery.AppHandler ->
                                "No notification is shown: the data goes straight to the app's push code, as if it arrived while the app is open."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = DebugKitColors.TextSecondary,
                    )
                    error?.let { Text(it, color = DebugKitColors.Error, style = MaterialTheme.typography.bodySmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        DebugKitButton(
                            "Send",
                            primary = true,
                            onClick = {
                                error = TestPushSender.send(context, current(), delivery)
                                if (error == null && delivery == PushDelivery.AppHandler) {
                                    Toast.makeText(context, "Sent to the app's handler", Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                        DebugKitButton(
                            "Save as template",
                            onClick = {
                                PushStore.saveTemplate(current().copy(title = title.trim().ifEmpty { "Untitled" }))
                                Toast.makeText(context, "Saved as a template", Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }
            }

            val templates = setup.templates + PushStore.savedTemplates
            if (templates.isNotEmpty()) {
                item { SectionHeader("Templates") }
                item {
                    Text(
                        "Tap to fill the form. Long-press one you saved to delete it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DebugKitColors.TextSecondary,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                    )
                }
                item {
                    CardGroup(templates) { template ->
                        val saved = template in PushStore.savedTemplates
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { fill(template) },
                                    onLongClick = if (saved) {
                                        { PushStore.deleteTemplate(template) }
                                    } else {
                                        null
                                    },
                                )
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                        ) {
                            Text(template.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = template.link ?: PushData.format(template.data).replace('\n', ' ').ifEmpty { template.body },
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = DebugKitFonts.Mono),
                                color = DebugKitColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            item { Box(Modifier.padding(bottom = 24.dp)) }
        }
    }

    @Composable
    private fun Token(provider: (suspend () -> String?)?) {
        val context = LocalContext.current
        if (provider == null) {
            Text(
                "Add token { … } to push { } in DebugKit.install to show it here.",
                style = MaterialTheme.typography.bodySmall,
                color = DebugKitColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            return
        }
        var reload by remember { mutableIntStateOf(0) }
        var token by remember { mutableStateOf<String?>(null) }
        var failure by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(reload) {
            token = null
            failure = null
            runCatching { provider() }
                .onSuccess { token = it ?: "" }
                .onFailure { failure = it.message ?: it.toString() }
        }
        DebugKitCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    failure != null -> Text("Could not get the token: $failure", color = DebugKitColors.Error, style = MaterialTheme.typography.bodySmall)
                    token == null -> Text("Loading…", style = MaterialTheme.typography.bodySmall, color = DebugKitColors.TextSecondary)
                    token!!.isEmpty() -> Text("The app returned no token.", style = MaterialTheme.typography.bodySmall)
                    else -> Text(token!!, style = MaterialTheme.typography.bodySmall.copy(fontFamily = DebugKitFonts.Mono), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DebugKitButton(
                        "Copy",
                        onClick = { token?.takeIf { it.isNotEmpty() }?.let { copyToClipboard(context, "Push token", it) } },
                        enabled = !token.isNullOrEmpty(),
                    )
                    DebugKitButton("Refresh", onClick = { reload++ })
                }
            }
        }
    }

    @Composable
    private fun ReceivedRow(push: ReceivedPush, expanded: Boolean, onToggle: () -> Unit, onResend: () -> Unit) {
        val context = LocalContext.current
        DebugKitCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), onClick = onToggle) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = push.title ?: if (push.body == null) "Data-only message" else "(no title)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                push.body?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = if (expanded) Int.MAX_VALUE else 2) }
                if (push.data.isNotEmpty()) {
                    Text(
                        text = if (expanded) PushData.format(push.data) else PushData.format(push.data).replace('\n', ' '),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = DebugKitFonts.Mono),
                        color = DebugKitColors.Accent,
                        maxLines = if (expanded) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(TIME.format(Date(push.timestamp)), style = MaterialTheme.typography.labelSmall, color = DebugKitColors.TextFaint)
                if (expanded) {
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DebugKitButton("Send again as a test", onClick = onResend)
                        DebugKitButton("Copy data", onClick = { copyToClipboard(context, "Push data", PushData.format(push.data)) })
                    }
                }
            }
        }
    }

    /** Test notifications need the permission on Android 13+; offers it, then app settings after a denial. */
    @Composable
    private fun NotificationPermission() {
        val context = LocalContext.current
        var canPost by remember { mutableStateOf(TestPushSender.canPost(context)) }
        var denied by rememberSaveable { mutableStateOf(false) }
        LifecycleResumeEffect(Unit) {
            canPost = TestPushSender.canPost(context)
            onPauseOrDispose { }
        }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            canPost = granted && TestPushSender.canPost(context)
            denied = !granted
        }
        if (canPost) return
        val canAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !denied
        Surface(
            color = DebugKitColors.WarningContainer,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Notifications are off for this app, so test notifications cannot be shown.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DebugKitColors.Warning,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                DebugKitButton(
                    if (canAsk) "Allow" else "Settings",
                    onClick = {
                        if (canAsk) {
                            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        }
                    },
                )
            }
        }
    }

    private val TIME = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
}
